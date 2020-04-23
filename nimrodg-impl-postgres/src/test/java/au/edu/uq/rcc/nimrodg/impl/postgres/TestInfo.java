package au.edu.uq.rcc.nimrodg.impl.postgres;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;

public class TestInfo {
	public final String user;
	public final String password;
	public final String host;
	public final String database;

	public TestInfo(String user, String password, String host, String database) {
		this.user = user;
		this.password = password;
		this.host = host;
		this.database = database;

		if(user == null || password == null || host == null || database == null) {
			throw new IllegalArgumentException();
		}
	}

	public Map<String, String> buildJdbcConfig() {
		return Map.of(
				"driver", org.postgresql.Driver.class.getCanonicalName(),
				"url", String.format("jdbc:postgresql://%s/%s", host, database),
				"username", user,
				"password", password
		);
	}

	public static TestInfo getFromEnvironment() {
		return new TestInfo(
				System.getenv("NIMRODG_TEST_PGUSER"),
				System.getenv("NIMRODG_TEST_PGPASSWORD"),
				System.getenv("NIMRODG_TEST_PGHOST"),
				System.getenv("NIMRODG_TEST_PGDATABASE")
		);
	}

	public static TestInfo getFromFilesystem() throws IOException {
		Path ti = Paths.get(System.getProperty("user.home")).resolve(".config/nimrod/pgtestinfo");

		Properties props = new Properties();
		try(InputStream is = Files.newInputStream(ti)) {
			props.load(is);
		}

		return new TestInfo(
				props.getProperty("user"),
				props.getProperty("password"),
				props.getProperty("host"),
				props.getProperty("database")
		);
	}

	public static TestInfo getBestEffort() throws IOException {
		TestInfo ti = null;

		try {
			ti = getFromEnvironment();
		} catch(IllegalArgumentException e) {
			/* nop */
		}

		if(ti == null) {
			ti = getFromFilesystem();
		}

		if(ti == null) {
			throw new IllegalArgumentException("Unable to get test info");
		}

		return ti;
	}
}