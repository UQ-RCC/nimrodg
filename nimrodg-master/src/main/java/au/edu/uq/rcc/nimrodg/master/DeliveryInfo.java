package au.edu.uq.rcc.nimrodg.master;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;

public class DeliveryInfo {

	public final String consumerTag;
	public final Envelope envelope;
	public final AMQP.BasicProperties properties;

	DeliveryInfo(String consumerTag, Envelope envelope, AMQP.BasicProperties properties) {
		this.consumerTag = consumerTag;
		this.envelope = envelope;
		this.properties = properties;
	}

}
