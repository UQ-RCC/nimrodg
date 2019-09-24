/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2019 The University of Queensland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.edu.uq.rcc.nimrodg.master;

import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.Actuator.LaunchResult;
import au.edu.uq.rcc.nimrodg.api.utils.NimrodUtils;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import au.edu.uq.rcc.nimrodg.api.Resource;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * AAAAA - Amazing Always Active Actuator Accelerator
 *
 * Manages actuator spawning.
 */
public abstract class AAAAA implements AutoCloseable {

	private final class ActuatorState {

		/* The actuator future itself. Is never obtruded. */
		public final CompletableFuture<Actuator> actuatorFuture;
		/* The one we give out to hang requests off. Obtruded with a CancellationException when shutdown() is called. */
		public final CompletableFuture<Actuator> launchFuture;

		public ActuatorState(CompletableFuture<Actuator> actuatorFuture, CompletableFuture<Actuator> publicLaunchFuture) {
			this.actuatorFuture = actuatorFuture;
			this.launchFuture = publicLaunchFuture;
		}
	}

	public final class LaunchRequest {

		public final UUID[] uuids;
		public final Resource resource;
		public final String rootPath;
		public final CompletableFuture<Actuator> actuatorFuture;
		public final CompletableFuture<LaunchResult[]> launchResults;

		private LaunchRequest(UUID[] uuids, Resource resource, CompletableFuture<Actuator> actuatorFuture, CompletableFuture<LaunchResult[]> launchResults) {
			this.uuids = uuids;
			this.resource = resource;
			this.rootPath = this.resource.getPath();
			this.actuatorFuture = actuatorFuture;
			this.launchResults = launchResults;
		}

	}

	private static final Logger LOGGER = LogManager.getLogger(AAAAA.class);

	private final LinkedBlockingDeque<LaunchRequest> requests;
	private final ConcurrentHashMap<Resource, ActuatorState> actuators;
	private final ExecutorService executor;
	private boolean isShutdown;

	public AAAAA() {
		requests = new LinkedBlockingDeque<>();
		actuators = new ConcurrentHashMap<>();
		executor = Executors.newCachedThreadPool();
		isShutdown = false;
	}

	public CompletableFuture<Actuator> getOrLaunchActuator(Resource root) {
		return getOrLaunchActuatorInternal(root).launchFuture;
	}

	private ActuatorState getOrLaunchActuatorInternal(Resource root) {
		return NimrodUtils.getOrAddLazy(actuators, root, key -> {
			CompletableFuture<Actuator> launchFuture = new CompletableFuture<>();

			return new ActuatorState(CompletableFuture.supplyAsync(() -> {
				try {
					return createActuator(key);
				} catch(IOException | IllegalArgumentException e) {
					throw new CompletionException(e);
				}
			}, executor).handleAsync((act, t) -> {
				// TODO: This needs to be handled better. Any pending launches should be cancelled.
				if(t != null) {
					LOGGER.error("Error launching actuator on '{}'", key.getPath());
					LOGGER.catching(t);
					actuators.remove(key);
					if(t instanceof CompletionException) {
						launchFuture.completeExceptionally(((CompletionException)t).getCause());
						throw (CompletionException)t;
					} else {
						launchFuture.completeExceptionally(t);
						throw new CompletionException(t);
					}
				} else {
					launchFuture.complete(act);
					return act;
				}
			}, executor), launchFuture);
		});
	}

	public static UUID[] generateRandomUUIDs(int n) {
		UUID[] u = new UUID[n];
		for(int i = 0; i < n; ++i) {
			u[i] = UUID.randomUUID();
		}
		return u;
	}

	public LaunchRequest launchAgents(Resource node, UUID[] uuids) {
		ActuatorState as = getOrLaunchActuatorInternal(node);

		CompletableFuture<LaunchResult[]> launchAnchor = new CompletableFuture<>();

		LaunchRequest rq = new LaunchRequest(uuids, node, as.launchFuture, launchAnchor.handleAsync((r, t) -> {
			if(r != null) {
				return r;
			}

			/* We've failed, so fail all the individual results, not the request. */
			Actuator.LaunchResult lr = new LaunchResult(node, t);
			Actuator.LaunchResult[] lrs = new LaunchResult[uuids.length];
			Arrays.setAll(lrs, i -> lr);
			return lrs;
		}, executor));

		launchAnchor.handleAsync((r, t) -> {
			requests.remove(rq);
			return null;
		}, executor);

		requests.addLast(rq);

		as.launchFuture.handleAsync((a, t) -> {
			/* If the actuator failed to launch, fail the launch future to trigger the above. */
			if(t != null) {
				launchAnchor.completeExceptionally(t);
				return null;
			}

			/* Try to launch the agent. If it goes badly, fail the future and let it handle cleanup. */
			Actuator.LaunchResult[] launchResults;
			try {
				synchronized(a) {
					/* NB: getNow() will never fail. */
					launchResults = a.launchAgents(rq.uuids);
				}
			} catch(IOException | RuntimeException e) {
				launchAnchor.completeExceptionally(e);
				return null;
			}

			launchAnchor.complete(launchResults);
			return null;
		}, executor);

		return rq;
	}

	public boolean isShutdown() {
		return this.isShutdown || executor.isShutdown() || executor.isTerminated();
	}

	/**
	 * Cancel any pending launches and prevent any new ones.
	 */
	public void shutdown() {
		if(isShutdown()) {
			return;
		}

		/* Prevent any further launches. */
		actuators.values().forEach(as -> as.launchFuture.obtrudeException(new CancellationException()));

		/* Abort pending launches. */
		requests.forEach(rq -> rq.launchResults.cancel(true));

		this.isShutdown = true;
	}

	@Override
	public void close() {
		/* Shutdown if we haven't already. */
		this.shutdown();

		/* Kill the actuators, keep the executor alive as the actuators may go wide to shut down.  */
		LOGGER.info("Waiting on {} actuator(s)...", actuators.size());
		CompletableFuture.allOf(actuators.values().stream()
				.map(as -> as.actuatorFuture.handleAsync((a, t) -> {
			if(a == null) {
				return null;
			}

			try {
				synchronized(a) {
					a.close();
				}
			} catch(IOException | RuntimeException ex) {
				LOGGER.error("close() failed on actuator for resource '{}'", a.getResource().getName());
				LOGGER.catching(ex);
			}
			return null;
		}, executor)).toArray(CompletableFuture[]::new)).join();
		actuators.clear();

		/* All actuators are down, terminate the executor. */
		shutdownAndAwaitTermination(executor);
	}

	/* Based off https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html */
	private void shutdownAndAwaitTermination(ExecutorService pool) {
		pool.shutdown(); // Disable new tasks from being submitted
		try {
			// Wait a while for existing tasks to terminate
			if(!pool.awaitTermination(60, TimeUnit.SECONDS)) {
				pool.shutdownNow(); // Cancel currently executing tasks
				// Wait a while for tasks to respond to being cancelled
				if(!pool.awaitTermination(60, TimeUnit.SECONDS)) {
					LOGGER.warn("Executor did not terminate, program may hang. Sorry.");
				}
			}
		} catch(InterruptedException ie) {
			// (Re-)Cancel if current thread also interrupted
			pool.shutdownNow();
			// Preserve interrupt status
			Thread.currentThread().interrupt();
		}
	}

	//protected abstract void reportLaunchFailure(UUID uuid, Resource node, Throwable t);
	protected abstract Actuator createActuator(Resource root) throws IOException, IllegalArgumentException;

	/**
	 * Run a task on the given resource's actuator sometime in the future.
	 *
	 * Tasks will be synchronized on the actuator. Shutdown will wait for tasks to finish.
	 *
	 * @param res The resource.
	 * @param proc The consumer to run.
	 */
	public void runWithActuator(Resource res, Consumer<Actuator> proc) {
		ActuatorState as = this.actuators.get(res);
		if(as == null) {
			throw new IllegalStateException();
		}

		as.launchFuture.thenAcceptAsync(act -> {
			synchronized(act) {
				if(!act.isClosed()) {
					proc.accept(act);
				}
			}
		}, executor);
	}
}
