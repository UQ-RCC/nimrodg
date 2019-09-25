package au.edu.uq.rcc.nimrodg.debug.agent;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonReader;
import javax.json.JsonStructure;

public class Message {

	public final byte[] data;
	public final Optional<JsonStructure> json;
	public final boolean incoming;

	private Message(byte[] data, Optional<JsonStructure> json, boolean incoming) {
		this.data = data;
		this.json = json;
		this.incoming = incoming;
	}

	public static Message create(byte[] data, boolean incoming) {
		JsonStructure js = null;
		try(JsonReader r = Json.createReader(new ByteArrayInputStream(data))) {
			try {
				js = r.read();
			} catch(JsonException e) {
				/* nop */
			}
		}
		return new Message(data, Optional.ofNullable(js), incoming);
	}
}
