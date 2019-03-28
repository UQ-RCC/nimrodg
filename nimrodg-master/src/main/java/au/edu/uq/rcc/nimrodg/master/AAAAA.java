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
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import au.edu.uq.rcc.nimrodg.api.Resource;
import java.util.Arrays;

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

		private LaunchRequest(UUID[] uuids, Resource resource, CompletableFuture<Actuator> actuatorFuture) {
			this.uuids = uuids;
			this.resource = resource;
			this.rootPath = this.resource.getPath();
			this.actuatorFuture = actuatorFuture;
			this.launchResults = new CompletableFuture<>();
		}

	}

	private static final Logger LOGGER = LogManager.getLogger(AAAAA.class);

	private final LinkedBlockingDeque<LaunchRequest> requests;
	private final AtomicBoolean wantStop;
	private final ConcurrentHashMap<Resource, ActuatorState> actuators;
	private final CompletableFuture<Void> shutdownFuture;

	public AAAAA() {
		requests = new LinkedBlockingDeque<>();
		wantStop = new AtomicBoolean(false);
		actuators = new ConcurrentHashMap<>();
		shutdownFuture = new CompletableFuture<>();
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
			}).handleAsync((act, t) -> {
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
			}), launchFuture);
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
		if(wantStop.get()) {
			throw new IllegalStateException();
		}

		ActuatorState as = getOrLaunchActuatorInternal(node);

		LaunchRequest rq = new LaunchRequest(uuids, node, as.launchFuture);

		/* If the launch failed, complete the future but mark each result as failed. */
		rq.launchResults.exceptionally(t -> {
			Actuator.LaunchResult lr = new LaunchResult(node, t);
			Actuator.LaunchResult[] lrs = new LaunchResult[uuids.length];
			Arrays.setAll(lrs, i -> lr);
			return lrs;
		});

		/* Remove our request whether we succeeded or failed. */
		rq.launchResults.handle((r, t) -> {
			requests.remove(rq);
			return null;
		});

		requests.addLast(rq);

		/* If the actuator failed to launch, fail the launch future to trigger the above. */
		as.launchFuture.exceptionally(t -> {
			rq.launchResults.completeExceptionally(t);
			return null;
		});

		/*
		 * If the actuator was created, actually launch the agents.
		 * NB: This must be async, we don't want it blocking this call.
		 */
		as.launchFuture.thenAcceptAsync(a -> {
			/* Try to launch the agent. If it goes badly, fail the future and let it handle cleanup. */
			Actuator.LaunchResult[] launchResults;
			try {
				synchronized(a) {
					/* NB: getNow() will never fail. */
					launchResults = a.launchAgents(rq.uuids);
				}
			} catch(IOException | RuntimeException e) {
				rq.launchResults.completeExceptionally(e);
				return;
			}

			rq.launchResults.complete(launchResults);
		});

		return rq;
	}

	public boolean isShutdown() {
		return shutdownFuture.isDone();
	}

	public CompletableFuture<Void> shutdown() {
		if(shutdownFuture.isDone()) {
			return shutdownFuture;
		}

		/*
		 * Shutdown Procedure:
		 * 1. Set the stop flag, interrupt the threads and wait for them to die.
		 * 2. Cancel any pending launches and wait on all the futures.
		 */
		wantStop.set(true);

		actuators.values().forEach(as -> as.launchFuture.obtrudeException(new CancellationException()));

		/* Stop pending launches. */
		CompletableFuture.runAsync(() -> {
			/* Kill any pending launches and gather their futures. */
			LOGGER.trace("Cancelling pending launches...");
			requests.forEach(rq -> rq.launchResults.cancel(true));
			requests.clear();
		}).thenRun(() -> shutdownFuture.complete(null));

		return shutdownFuture;
	}

	@Override
	public void close() {
		/* Shutdown if we haven't already. */
		this.shutdown().join();

		/* Kill the actuators. */
		LOGGER.info("Waiting on {} actuator(s)...", actuators.size());

		CompletableFuture.allOf(actuators.values().stream()
				.map(as -> as.actuatorFuture.handle((a, t) -> {
			if(a == null) {
				return null;
			}

			String path = a.getResource().getPath();
			try {
				synchronized(a) {
					a.close();
				}
			} catch(IOException | RuntimeException ex) {
				LOGGER.error("close() failed on actuator for resource '{}'", path);
				LOGGER.catching(ex);
			}
			return null;
		})).toArray(CompletableFuture[]::new)).join();
		actuators.clear();
	}

	//protected abstract void reportLaunchFailure(UUID uuid, Resource node, Throwable t);
	protected abstract Actuator createActuator(Resource root) throws IOException, IllegalArgumentException;
}
