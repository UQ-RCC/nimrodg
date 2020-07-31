package au.edu.uq.rcc.nimrodg.master;

import au.edu.uq.rcc.nimrodg.agent.messages.AgentMessage;
import java.io.IOException;

public interface AMQProcessor extends AutoCloseable {

	String getQueue();

	String getExchange();

	void sendMessage(String key, AgentMessage msg) throws IOException;

	void opMessage(MessageQueueListener.MessageOperation op, long tag);
}
