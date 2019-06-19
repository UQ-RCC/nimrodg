package au.edu.uq.rcc.nimrodg.test;

import au.edu.uq.rcc.nimrodg.agent.messages.AgentMessage;
import au.edu.uq.rcc.nimrodg.master.AMQProcessor;
import java.io.IOException;

public class DummyAMQPProcessor implements AMQProcessor {

	public final String queue;
	public final String exchange;

	public DummyAMQPProcessor(String queue, String exchange) {
		this.queue = queue;
		this.exchange = exchange;
	}

	@Override
	public void close() throws Exception {

	}

	@Override
	public String getQueue() {
		return this.queue;
	}

	@Override
	public String getExchange() {
		return this.exchange;
	}

	@Override
	public void sendMessage(String key, AgentMessage msg) throws IOException {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

}
