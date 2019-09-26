package au.edu.uq.rcc.nimrodg.debug.agent;

import au.edu.uq.rcc.nimrodg.agent.messages.json.JsonBackend;
import au.edu.uq.rcc.nimrodg.master.AMQPMessage;
import com.rabbitmq.client.AMQP;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonReader;
import javax.json.JsonStructure;

public class Message {

	public final AMQPMessage message;
	public final Optional<JsonStructure> rawJson;
	public final Optional<JsonStructure> msgJson;
	public final boolean incoming;

	private Message(AMQPMessage message, Optional<JsonStructure> rawJson, Optional<JsonStructure> msgJson, boolean incoming) {
		this.message = message;
		this.rawJson = rawJson;
		this.msgJson = msgJson;
		this.incoming = incoming;
	}

	public static Message create(AMQPMessage msg, boolean incoming) {
		/* Try to parse it "raw", as this will keep unknown fields. */
		JsonStructure rawJson = null;
		try(JsonReader r = Json.createReader(new ByteArrayInputStream(msg.body))) {
			try {
				rawJson = r.read();
			} catch(JsonException e) {
				/* nop */
			}
		}

		/* If the message was deserialised correctly, re-serialise it for viewing. */
		JsonStructure msgJson = null;
		if(msg.message != null) {
			msgJson = JsonBackend.INSTANCE.toJson(msg.message);
		}

		return new Message(msg, Optional.ofNullable(rawJson), Optional.ofNullable(msgJson), incoming);
	}
}
