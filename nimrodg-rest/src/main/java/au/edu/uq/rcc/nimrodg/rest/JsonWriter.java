package au.edu.uq.rcc.nimrodg.rest;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import javax.json.JsonStructure;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

@Provider
@Produces("application/json")
public class JsonWriter implements MessageBodyWriter<JsonStructure> {

	@Override
	public boolean isWriteable(Class<?> type, Type type1, Annotation[] antns, MediaType mt) {
		return JsonStructure.class.isAssignableFrom(type);
	}

	@Override
	public void writeTo(JsonStructure t, Class<?> type, Type type1, Annotation[] antns, MediaType mt, MultivaluedMap<String, Object> mm, OutputStream out) throws IOException, WebApplicationException {
		out.write(t.toString().getBytes(StandardCharsets.UTF_8));
		out.flush();
	}

}
