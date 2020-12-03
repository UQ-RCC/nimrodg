package au.edu.uq.rcc.nimrodg.api;

import javax.json.JsonObject;
import java.time.Instant;
import java.util.UUID;

public interface AgentInfo {

	/**
	 * All the possible server-side states for an agent.
	 */
	enum State {
		/**
		 * No agent is connected, waiting for initialisation.
		 */
		WAITING_FOR_HELLO,
		/**
		 * The agent is ready and willing to accept jobs.
		 */
		READY,
		/**
		 * The agent is currently processing a job. No new jobs may be submitted.
		 */
		BUSY,
		/**
		 * The agent has shutdown.
		 */
		SHUTDOWN
	}

	enum ShutdownReason {
		HostSignal,
		Requested
	}

	/**
	 * Retrieve the current server-side state of the agent.
	 *
	 * @return The current server-side state of the agent.
	 */
	State getState();

	/**
	 * Retrieve the name of the AMQP queue the agent is on.
	 *
	 * @return The name of the AMQP queue the agent is on. If {@link #getState()} returns
	 * {@link State#WAITING_FOR_HELLO}, this value is null.
	 */
	String getQueue();

	/**
	 * Retrieve the UUID of the agent.
	 *
	 * @return The UUID of the agent. If {@link #getState()} returns {@link State#WAITING_FOR_HELLO}, this value is
	 * null.
	 */
	UUID getUUID();

	/**
	 * Get the POSIX signal that caused the agent to shutdown.
	 *
	 * @return The POSIX signal that caused the agent to shutdown. This is only meaningful if {@link #getState()}
	 * returns {@link State#SHUTDOWN} and {@link #getShutdownReason()} returns
	 * {@link ShutdownReason#HostSignal}.
	 */
	int getShutdownSignal();

	/**
	 * Get the reason the agent shutdown.
	 *
	 * @return The reason the agent shutdown.
	 */
	ShutdownReason getShutdownReason();

	Instant getCreationTime();

	// The time at which the state changed from WAITING_FOR_HELLO to READY
	Instant getConnectionTime();

	/**
	 * Get the instant at which the last message was received.
	 * @return The instant at which the last message was received, or null if not heard from.
	 */
	Instant getLastHeardFrom();

	/**
	 * Get the Unix timestamp at which this agent should be expired.
	 *
	 * @return The Unix timestamp at which this agent should be expired. If no expiry, returns {@link Instant#MAX}
	 */
	Instant getExpiryTime();

	boolean getExpired();

	String getSecretKey();

	JsonObject getActuatorData();
}
