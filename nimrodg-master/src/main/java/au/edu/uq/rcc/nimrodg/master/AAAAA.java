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

/**
 * AAAAA - Amazing Always Active Actuator Accelerator
 *
 * Manages a pool of threads, handling actuator setup and agent spawns.
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

	private final LinkedBlockingDeque<LaunchRequest> m_Requests;
	private final AtomicBoolean m_WantStop;
	private final ConcurrentHashMap<Resource, ActuatorState> m_Actuators;
	private final CompletableFuture<Void> m_ShutdownFuture;

	public AAAAA() {
		m_Requests = new LinkedBlockingDeque<>();
		m_WantStop = new AtomicBoolean(false);
		m_Actuators = new ConcurrentHashMap<>();
		m_ShutdownFuture = new CompletableFuture<>();
	}

	public CompletableFuture<Actuator> getOrLaunchActuator(Resource root) {
		return getOrLaunchActuatorInternal(root).launchFuture;
	}

	private ActuatorState getOrLaunchActuatorInternal(Resource root) {
		return NimrodUtils.getOrAddLazy(m_Actuators, root, key -> {
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
					m_Actuators.remove(key);
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

	public LaunchRequest launchAgents(Resource node, int num) {
		if(m_WantStop.get()) {
			throw new IllegalStateException();
		}

		UUID[] uuids = generateRandomUUIDs(num);

		ActuatorState as = getOrLaunchActuatorInternal(node);

		LaunchRequest rq = new LaunchRequest(uuids, node, as.launchFuture);

		/* Set up the failure code. If this happens, no agents from the batch were launched.*/
		rq.launchResults.exceptionally(t -> {
			Throwable at = t;
			if(t instanceof CancellationException) {
				for(int i = 0; i < rq.uuids.length; ++i) {
					LOGGER.info("Agent '{}' launch on '{}' cancelled.", rq.uuids[i], node.getPath());
				}
				if(!m_Requests.remove(rq)) {
					/* cancel() has been called on the future, but we've already started spawning. Tough titties. */
					LOGGER.warn("Can't cancel, already spawning. Agent will be rejected...");
				}
			} else {
				for(int i = 0; i < rq.uuids.length; ++i) {
					LOGGER.info("Agent '{}' launch on '{}' failed.", rq.uuids[i], node.getPath());
				}
				if(at instanceof CompletionException) {
					at = ((CompletionException)at).getCause();
				}

				LOGGER.catching(t);
			}

			for(int i = 0; i < rq.uuids.length; ++i) {
				reportLaunchFailure(rq.uuids[i], rq.resource, at);
			}

			return null;
		});

		m_Requests.addLast(rq);

		/* NB: This must be async, we don't want it blocking this call. */
		as.launchFuture.handleAsync((a, t) -> {
			/* If the actuator failed to launch, fail the future and let it cleanup. */
			if(t != null) {
				rq.launchResults.completeExceptionally(t);
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
				rq.launchResults.completeExceptionally(e);
				return null;
			}

			/* At least one agent was spawned, complete the future */
			for(int i = 0; i < launchResults.length; ++i) {
				assert (rq.resource.equals(launchResults[i].node));
				if(launchResults[i].node == null) {
					reportLaunchFailure(rq.uuids[i], rq.resource, launchResults[i].t);
				} else {
					LOGGER.trace("Actuator placed agent {} on '{}'", rq.uuids[i], launchResults[i].node.getPath());
				}
			}

			rq.launchResults.complete(launchResults);
			return null;
		});

		return rq;
	}

	public boolean isShutdown() {
		return m_ShutdownFuture.isDone();
	}

	public CompletableFuture<Void> shutdown() {
		if(m_ShutdownFuture.isDone()) {
			return m_ShutdownFuture;
		}

		/*
		 * Shutdown Procedure:
		 * 1. Set the stop flag, interrupt the threads and wait for them to die.
		 * 2. Cancel any pending launches and wait on all the futures.
		 */
		m_WantStop.set(true);

		m_Actuators.values().forEach(as -> as.launchFuture.obtrudeException(new CancellationException()));

		/* Stop pending launches. */
		CompletableFuture.runAsync(() -> {
			/* Kill any pending launches and gather their futures. */
			LOGGER.trace("Cancelling pending launches...");
			m_Requests.forEach(rq -> rq.launchResults.cancel(true));
			m_Requests.clear();
		}).thenRun(() -> m_ShutdownFuture.complete(null));

		return m_ShutdownFuture;
	}

	@Override
	public void close() {
		/* Shutdown if we haven't already. */
		this.shutdown().join();

		/* Kill the actuators. */
		LOGGER.info("Waiting on {} actuator(s)...", m_Actuators.size());

		CompletableFuture.allOf(m_Actuators.values().stream()
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
		m_Actuators.clear();
	}

	protected abstract void reportLaunchFailure(UUID uuid, Resource node, Throwable t);

	protected abstract Actuator createActuator(Resource root) throws IOException, IllegalArgumentException;
}
