package au.edu.uq.rcc.nimrodg.portal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest
public class RabbitManagementClientTests {

	@Autowired
	private RabbitManagementClient rmq;

	@Test
	public void rabbitMqClientTest() {
		ResponseEntity<Void> resp;

		resp = rmq.addUser("testuser", "testpass");
		Assertions.assertNotNull(resp);

		if(TestUtils.IS_CI) {
			Assertions.assertEquals(HttpStatus.CREATED, resp.getStatusCode());
		} else {
			Assertions.assertTrue(HttpStatus.CREATED.equals(resp.getStatusCode()) || HttpStatus.NO_CONTENT.equals(resp.getStatusCode()));
		}

		resp = rmq.addUser("testuser", "testpass");
		Assertions.assertNotNull(resp);
		Assertions.assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());

		resp = rmq.addVHost("testuser");
		Assertions.assertNotNull(resp);
		if(TestUtils.IS_CI) {
			Assertions.assertEquals(HttpStatus.CREATED, resp.getStatusCode());
		} else {
			Assertions.assertTrue(HttpStatus.CREATED.equals(resp.getStatusCode()) || HttpStatus.NO_CONTENT.equals(resp.getStatusCode()));
		}

		resp = rmq.addVHost("testuser");
		Assertions.assertNotNull(resp);
		Assertions.assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());

		resp = rmq.addPermissions("testuser", "testuser", ".*", ".*", ".*");
		Assertions.assertNotNull(resp);
		if(TestUtils.IS_CI) {
			Assertions.assertEquals(HttpStatus.CREATED, resp.getStatusCode());
		} else {
			Assertions.assertTrue(HttpStatus.CREATED.equals(resp.getStatusCode()) || HttpStatus.NO_CONTENT.equals(resp.getStatusCode()));
		}

		resp = rmq.addPermissions("testuser", "testuser", ".*", ".*", ".*");
		Assertions.assertNotNull(resp);
		Assertions.assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
	}
}
