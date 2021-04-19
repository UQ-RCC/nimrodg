package au.edu.uq.rcc.nimrodg.master;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class HeartTests {
	enum Op {
		Create,
		Connect,
		Ping,
		Pong,
		RequestTerminate,
		Terminate
	}

	public static class Event {
		final Instant timestamp;
		final Op operation;

		Event(Instant timestamp, Op operation) {
			this.timestamp = timestamp;
			this.operation = operation;
		}
	}

	@Test
	public void heartTest() {

		/* We choose a well-defined start point. */
		Instant now = Instant.EPOCH;

		TestOps.AgentDef[] agents = new TestOps.AgentDef[]{
//				new TestOps.AgentDef(UUID.fromString("00000000-0000-0000-0000-000000000000"), now.plusSeconds(1), now.plusSeconds(10), Instant.MAX, 0, 0),
//				new TestOps.AgentDef(UUID.fromString("00000000-0000-0000-0000-000000000001"), now.plusSeconds(1), now.plusSeconds(10), Instant.MAX, 1, 0),
//				new TestOps.AgentDef(UUID.fromString("00000000-0000-0000-0000-000000000002"), now.plusSeconds(1), now.plusSeconds(10), Instant.MAX, 10, 0),
//				new TestOps.AgentDef(UUID.fromString("00000000-0000-0000-0000-000000000003"), now.plusSeconds(1), now.plusSeconds(10), Instant.MAX, 100, 0),
//				new TestOps.AgentDef(UUID.fromString("00000000-0000-0000-0000-000000000004"), now.plusSeconds(1), now.plusSeconds(10), Instant.MAX, 100, 0),
//				new TestOps.AgentDef(UUID.fromString("00000000-0000-0000-0000-000000000005"), now.plusSeconds(1), now.plusSeconds(10), Instant.MAX, 100, 0),
//				new TestOps.AgentDef(UUID.fromString("00000000-0000-0000-0000-000000000006"), now, Instant.MAX, now.plusSeconds(60), 0, 10),
				/* Good, short-lived agent. Terminates when asked. */
				new TestOps.AgentDef(UUID.fromString("00000000-0000-0000-0000-000000000007"), now, now.plusSeconds(1), now.plusSeconds(10), 0, 0),
		};

		Event[] evts = new Event[]{
				new Event(now.plusSeconds(0), Op.Create),
				new Event(now.plusSeconds(1), Op.Connect),
				new Event(now.plusSeconds(7), Op.Ping),
				new Event(now.plusSeconds(8), Op.Pong),
				new Event(now.plusSeconds(10), Op.RequestTerminate),
				new Event(now.plusSeconds(11), Op.Terminate),
		};
		TestOps tops = new TestOps();
		tops.init(agents, now);

		for(int i = 0; i < 2000; ++i) {
			System.err.printf("[%s] Tick %d\n", tops.getCurrentTime(), i);
			if(!tops.tick()) {
				break;
			}
		}
		//Heart heart = new Heart();
	}
}
