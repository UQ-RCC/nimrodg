package au.edu.uq.rcc.nimrodg.master;

import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.utils.NimrodUtils;

import java.time.Instant;
import java.util.*;

public class TestOps implements Heart.Operations {

	//private final TreeMap<Instant, Deque<Runnable>> tasks;
	private final PriorityQueue<Map.Entry<Instant, Runnable>> timedTasks;
	private final Deque<Runnable> tasks;

	private final Set<UUID> toExpire;
	private final Map<UUID, MiniAgentState> agents;
	private final Heart heart;
	private Instant startTime;
	private Instant currentTime;
	private Instant nextTime;

	private Long expiryRetryInterval;
	private Long expiryRetryCount;
	private Long heartbeatInterval;
	private Long heartbeatMissedThreshold;

	static class AgentDef {
		final UUID uuid;
		final Instant creationTime;
		final Instant connectTime;
		final Instant expiryTime;
		final long delaySeconds;
		/**
		 * How many times to ignore an "expire" request.
		 */
		final long terminationIgnores;

		public AgentDef(UUID uuid, Instant creationTime, Instant connectTime, Instant expiryTime, long delaySeconds, long terminationIgnores) {
			this.uuid = uuid;
			this.creationTime = creationTime;
			this.connectTime = connectTime;
			this.expiryTime = expiryTime;
			this.delaySeconds = delaySeconds;
			this.terminationIgnores = terminationIgnores;
		}
	}

	private static class MiniAgentState {
		final AgentDef def;
		Instant lastHeardFrom;
		long expiryIgnoresLeft;

		MiniAgentState(AgentDef def) {
			this.def = def;
			this.lastHeardFrom = null;
			this.expiryIgnoresLeft = def.terminationIgnores;
		}
	}

	public TestOps() {
		this.timedTasks = new PriorityQueue<>(Comparator.comparing(Map.Entry::getKey));
		this.tasks = new ArrayDeque<>();
		this.toExpire = new HashSet<>();
		this.agents = new HashMap<>();
		this.heart = new Heart(this);

		this.expiryRetryInterval = null;
		this.expiryRetryCount = null;
		this.heartbeatInterval = null;
		this.heartbeatMissedThreshold = null;
	}


	public void init(AgentDef[] agents, Instant now) {
		this.startTime = now;
		this.currentTime = now;
		this.nextTime = this.currentTime.plusSeconds(1);

		this.agents.clear();
		this.toExpire.clear();

		if(Arrays.stream(agents).map(a -> a.uuid).count() != agents.length) {
			throw new IllegalArgumentException("duplicate agent uuid");
		}

		for(AgentDef def : agents) {
			queueEvent(def.creationTime, () -> {
				System.err.printf("[%s] %s: Create\n", currentTime, def.uuid);
				this.agents.put(def.uuid, new MiniAgentState(def));
				this.heart.onAgentCreate(def.uuid, def.creationTime);
			});

			queueEvent(def.connectTime, () -> {
				System.err.printf("[%s] %s: Connect\n", currentTime, def.uuid);
				this.agents.get(def.uuid).lastHeardFrom = currentTime;
			});
		}

		setExpiryRetryInterval(Heart.DEFAULT_EXPIRY_RETRY_INTERVAL);
		setExpiryRetryCount(Heart.DEFAULT_EXPIRY_RETRY_COUNT);
		setHeartbeatInterval(Heart.DEFAULT_HEARTBEAT_INTERVAL);
		setHeartbeatMissedThreshold(Heart.DEFAULT_HEARTBEAT_MISSED_THRESHOLD);
	}

	public void setExpiryRetryInterval(long v) {
		heart.onConfigChange("nimrod.master.heart.expiry_retry_interval", String.valueOf(expiryRetryInterval), String.valueOf(v));
		expiryRetryInterval = v;
	}

	public void setExpiryRetryCount(long v) {
		heart.onConfigChange("nimrod.master.heart.expiry_retry_count", String.valueOf(expiryRetryCount), String.valueOf(v));
		expiryRetryCount = v;
	}

	public void setHeartbeatInterval(long v) {
		heart.onConfigChange("nimrod.master.heart.interval", String.valueOf(heartbeatInterval), String.valueOf(v));
		heartbeatInterval = v;
	}

	public void setHeartbeatMissedThreshold(long v) {
		heart.onConfigChange("nimrod.master.heart.missed_threshold", String.valueOf(heartbeatMissedThreshold), String.valueOf(v));
		heartbeatMissedThreshold = v;
	}

	/* Each "tick" increments the current time by one second. */
	public boolean tick() {
		nextTime = currentTime.plusSeconds(1);

		runEvents();

		toExpire.clear();

		while(!tasks.isEmpty()) {
			tasks.poll().run();
		}

		heart.tick(currentTime);
		toExpire.forEach(this::removeAgent);

		currentTime = nextTime;
		return !agents.isEmpty();
	}

	public Instant getStartTime() {
		return this.startTime;
	}

	public Instant getNextTime() {
		return this.nextTime;
	}

	public Instant getCurrentTime() {
		return this.currentTime;
	}

	@Override
	public void expireAgent(UUID u) {
		System.err.printf("[%s] %s: Expire\n", currentTime, u);

		MiniAgentState mas = this.agents.get(u);
		//mas.
		this.toExpire.add(u);
	}

	@Override
	public void terminateAgent(UUID u) {
		System.err.printf("[%s] %s: RequestTerminate\n", currentTime, u);
		runLater(() -> {
			MiniAgentState mas = this.agents.get(u);
			if(mas.expiryIgnoresLeft == 0) {
				removeAgent(u);
			} else {
				System.err.printf("[%s] %s: Ignoring termination, %d left\n", currentTime, u, mas.expiryIgnoresLeft);
				--mas.expiryIgnoresLeft;
			}
		});
	}

	@Override
	public void disconnectAgent(UUID u, AgentInfo.ShutdownReason reason, int signal) {
		System.err.printf("[%s] %s: Force Disconnect, %s, %d\n", currentTime, u, reason, signal);
		runLater(() -> removeAgent(u));
	}

	private void removeAgent(UUID u) {
		System.err.printf("[%s] %s: Terminate\n", currentTime, u);
		heart.onAgentDisconnect(u);
		agents.remove(u);
	}

	@Override
	public void pingAgent(UUID u) {
		System.err.printf("[%s] %s: Ping\n", currentTime, u);
		MiniAgentState mas = this.agents.get(u);
		/* Queue up a "pong" for next tick + delay */
		queueEvent(nextTime.plusSeconds(mas.def.delaySeconds), () -> {
			System.err.printf("[%s] %s: Pong\n", currentTime, u);
			mas.lastHeardFrom = currentTime;
			heart.onAgentPong(u);
		});
	}

	@Override
	public Instant getLastHeardFrom(UUID u) {
		MiniAgentState mas = this.agents.get(u);
		return NimrodUtils.coalesce(mas.lastHeardFrom, mas.def.connectTime, mas.def.creationTime);
	}

	@Override
	public Instant getWalltime(UUID u) {
		return this.agents.get(u).def.expiryTime;
	}

	@Override
	public void logInfo(String fmt, Object... args) {
		System.err.printf("[%s] %s\n", currentTime, String.format(fmt, args));
	}

	@Override
	public void logTrace(String fmt, Object... args) {
		System.err.printf("[%s] %s\n", currentTime, String.format(fmt, args));
	}

	private void runLater(Runnable r) {
		tasks.add(r);
	}

	private void queueEvent(Instant time, Runnable r) {
		timedTasks.add(Map.entry(time, r));
	}

	/* Run events up until the current time. */
	private void runEvents() {
		while(!timedTasks.isEmpty()) {
			if(timedTasks.peek().getKey().isAfter(currentTime)) {
				return;
			}

			timedTasks.poll().getValue().run();
		}
	}
}
