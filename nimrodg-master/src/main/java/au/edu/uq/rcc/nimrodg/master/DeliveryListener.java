package au.edu.uq.rcc.nimrodg.master;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;

public interface DeliveryListener {
	void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body);
}
