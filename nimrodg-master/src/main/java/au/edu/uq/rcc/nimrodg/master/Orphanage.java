package au.edu.uq.rcc.nimrodg.master;

import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.NimrodAPIException;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.api.ResourceFullException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;

/**
 * The orphanage, handles recovered agents that all actuators have rejected.
 */
public final class Orphanage implements Actuator {

	private final Resource resource;
	private final HashSet<UUID> agents;
	private boolean closed;

	public Orphanage() {
		this.resource = null;
		this.agents = new HashSet<>();
		this.closed = false;
	}

	@Override
	public Resource getResource() throws NimrodAPIException {
		return resource;
	}

	@Override
	public LaunchResult[] launchAgents(UUID[] uuids) {
		LaunchResult[] lrs = new LaunchResult[uuids.length];
		Arrays.fill(lrs, new LaunchResult(null, new ResourceFullException(resource)));
		return lrs;
	}

	@Override
	public void forceTerminateAgent(UUID[] uuid) {
		this.agents.removeAll(Arrays.asList(uuid));
	}

	@Override
	public void notifyAgentConnection(AgentState state) {
		this.agents.add(state.getUUID());
	}

	@Override
	public void notifyAgentDisconnection(UUID uuid) {
		this.agents.remove(uuid);
	}

	@Override
	public boolean canSpawnAgents(int num) throws IllegalArgumentException {
		return false;
	}

	@Override
	public boolean adopt(AgentState state) {
		this.agents.add(state.getUUID());
		return true;
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	@Override
	public void close() {
		if(this.closed) {
			return;
		}
		this.agents.clear();
		this.closed = true;
	}
}
