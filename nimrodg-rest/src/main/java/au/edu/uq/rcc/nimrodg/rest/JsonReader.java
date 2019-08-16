package au.edu.uq.rcc.nimrodg.rest;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Stack;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParser.Event;
import javax.json.stream.JsonParsingException;
import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;

@Provider
@Consumes("application/json")
public class JsonReader implements MessageBodyReader<JsonStructure> {

	@Override
	public boolean isReadable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
		return JsonStructure.class.isAssignableFrom(type);
	}

	@Override
	public JsonStructure readFrom(Class<JsonStructure> type, Type type1, Annotation[] antns, MediaType mt, MultivaluedMap<String, String> mm, InputStream in) throws IOException, WebApplicationException {
		try(javax.json.JsonReader jr = Json.createReader(in)) {
			return jr.read();
		} catch(JsonParsingException e) {
			throw new WebApplicationException(Response.Status.BAD_REQUEST);
		}
	}

}
