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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import au.edu.uq.rcc.nimrodg.api.Resource;

/**
 * AAAAA - Amazing Always Active Actuator Accelerator
 *
 * Manages a pool of threads, handling actuator setup and agent spawns.
 */
public abstract class AAAAA implements AutoCloseable {

	private class ActuatorState {

		public final CompletableFuture<Actuator> actuator;

		/**
		 * An actuator is "busy" if it's currently launching an agent or in a shutdown procedure.
		 */
		public final AtomicBoolean busy;

		public ActuatorState(CompletableFuture<Actuator> actuator) {
			this.actuator = actuator;
			this.busy = new AtomicBoolean(false);
		}

	}

	public final class LaunchRequest {

		private final ActuatorState actstate;

		public final UUID[] uuids;
		public final Resource resource;
		public final String rootPath;
		public final CompletableFuture<Actuator> actuatorFuture;
		public final CompletableFuture<LaunchResult[]> launchResults;

		private LaunchRequest(UUID[] uuids, Resource resource, ActuatorState actstate) {
			this.actstate = actstate;
			this.uuids = uuids;
			this.resource = resource;
			this.rootPath = this.resource.getPath();
			this.actuatorFuture = actstate.actuator;
			this.launchResults = new CompletableFuture<>();
		}

	}

	private static final Logger LOGGER = LogManager.getLogger(AAAAA.class);

	private final ExecutorService m_Pool;
	private final LinkedBlockingDeque<LaunchRequest> m_Requests;
	private final AtomicBoolean m_WantStop;
	private final ConcurrentHashMap<Resource, ActuatorState> m_Actuators;
	private final CompletableFuture<Void> m_ShutdownFuture;

	public AAAAA(int numThreads) {
		m_Pool = Executors.newFixedThreadPool(numThreads);
		m_Requests = new LinkedBlockingDeque<>();
		m_WantStop = new AtomicBoolean(false);
		m_Actuators = new ConcurrentHashMap<>();
		m_ShutdownFuture = new CompletableFuture<>();

		for(int i = 0; i < numThreads; ++i) {
			m_Pool.execute(() -> proc());
		}
	}

	private void proc() {
		while(!m_WantStop.get()) {
			/* Try to grab a request. */
			LaunchRequest rq;
			try {
				rq = m_Requests.take();
			} catch(InterruptedException e) {
				continue;
			}

			if(rq == null) {
				continue;
			}

			/* If the actuator hasn't spawned or it's busy, ignore it. */
			if(!rq.actstate.actuator.isDone() || !rq.actstate.busy.compareAndSet(false, true)) {
				m_Requests.addFirst(rq);
				continue;
			}

			try {
				/* Try to launch the agent. If it goes badly, fail the future and let it handle cleanup. */
				Actuator.LaunchResult[] launchResults;
				try {
					/* NB: getNow() will never fail. */
					launchResults = rq.actstate.actuator.getNow(null).launchAgents(rq.uuids);
				} catch(IOException | RuntimeException e) {
					rq.launchResults.completeExceptionally(e);
					continue;
				}

				/* At least one agent was spawned, complete the future */
				for(int i = 0; i < launchResults.length; ++i) {
					assert(rq.resource.equals(launchResults[i].node));
					if(launchResults[i].node == null) {
						reportLaunchFailure(rq.uuids[i], rq.resource, launchResults[i].t);
					} else {
						LOGGER.trace("Actuator placed agent {} on '{}'", rq.uuids[i], launchResults[i].node.getPath());
					}
				}

				rq.launchResults.complete(launchResults);
			} finally {
				rq.actstate.busy.set(false);
			}
		}
	}

	/*
	 * THIS FUNCTION IS BAD. IT SHOULD NOT BE USED.
	 */
	@Deprecated
	public CompletableFuture<Actuator> getOrLaunchActuator(Resource root) {
		// TODO: Rewrite the code that uses this to actually use the future properly.
		return _getOrLaunchActuator(root).actuator;
	}

	private ActuatorState _getOrLaunchActuator(Resource root) {
		return NimrodUtils.getOrAddLazy(m_Actuators, root, key -> {
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
					return null;
				} else {
					return act;
				}
			}));
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

		ActuatorState as = _getOrLaunchActuator(node);

		LaunchRequest rq = new LaunchRequest(uuids, node, as);

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

		return rq;
	}

	public boolean isShutdown() {
		return m_ShutdownFuture.isDone();
	}

	public CompletableFuture<Void> shutdown() {
		if(m_Pool.isShutdown() || m_ShutdownFuture.isDone()) {
			return m_ShutdownFuture;
		}

		/*
		 * Shutdown Procedure:
		 * 1. Set the stop flag, interrupt the threads and wait for them to die.
		 * 2. Cancel any pending launches and wait on all the futures.
		 */
		m_WantStop.set(true);
		m_Pool.shutdownNow();

		/* Stop pending launches. */
		CompletableFuture.runAsync(() -> {
			try {
				m_Pool.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
			} catch(InterruptedException e) {
				// nop
			}

			/* Kill any pending launches and gather their futures. */
			LOGGER.trace("Cancelling pending launches...");
			m_Requests.forEach(rq -> rq.launchResults.cancel(true));
			m_Requests.clear();

			m_Actuators.values().forEach(as -> as.actuator.cancel(true));

		}).thenRun(() -> m_ShutdownFuture.complete(null));

		return m_ShutdownFuture;
	}

	@Override
	public void close() {
		/* Shutdown if we haven't already. */
		this.shutdown().join();

		/* Kill the actuators. */
		LOGGER.trace("Waiting on {} actuator futures...", m_Actuators.size());
		CompletableFuture.allOf(m_Actuators.values().stream()
				.map(as -> as.actuator.handleAsync((a, t) -> {
			if(a == null) {
				return null;
			}

			String path = a.getNode().getPath();
			try {
				a.close();
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
