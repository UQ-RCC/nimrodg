package au.edu.uq.rcc.nimrodg.portal;

import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class UserState {
	public final long id;
	public final String username;
	public final String dbUser;
	public final String dbPass;

	public final String amqpUser;
	public final String amqpPass;
	public final boolean initialised;

	public final String jdbcUrl;

	public final Map<String, String> vars;

	public UserState(long id, String username, String dbUser, String dbPass, String amqpUser, String amqpPass, boolean initialised, String jdbcUrl, Map<String, String> vars) {
		this.id = id;
		this.username = username;
		this.dbUser = dbUser;
		this.dbPass = dbPass;
		this.amqpUser = amqpUser;
		this.amqpPass = amqpPass;
		this.initialised = initialised;
		this.jdbcUrl = jdbcUrl;
		this.vars = Map.copyOf(vars);
	}
}
