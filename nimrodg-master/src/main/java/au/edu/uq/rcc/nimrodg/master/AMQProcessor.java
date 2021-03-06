package au.edu.uq.rcc.nimrodg.master;

import au.edu.uq.rcc.nimrodg.agent.messages.AgentMessage;

import java.io.IOException;

public interface AMQProcessor extends AutoCloseable {

	String getQueue();

	String getExchange();

	String getSigningAlgorithm();

	AMQPMessage sendMessage(String key, String accessKey, String secretKey, AgentMessage msg) throws IOException;

	void opMessage(MessageQueueListener.MessageOperation op, long tag);
}
