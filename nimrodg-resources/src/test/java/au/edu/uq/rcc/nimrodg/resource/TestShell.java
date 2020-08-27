package au.edu.uq.rcc.nimrodg.resource;

import au.edu.uq.rcc.nimrodg.resource.ssh.TransportFactory;
import au.edu.uq.rcc.nimrodg.shell.RemoteShell;

import javax.json.JsonObject;
import javax.json.JsonValue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.security.PublicKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

public class TestShell implements RemoteShell {

	private final Path home;
	private final FileSystem fs;
	private final Map<String, BiFunction<String[], byte[], CommandResult>> commands;

	public TestShell(Path home) {
		this.home = home;
		this.fs = home.getFileSystem();
		this.commands = new HashMap<>();

		commands.put("env", this::env);
		commands.put("mkdir", this::mkdir);
	}

	public void addCommandProcessor(String argv0, BiFunction<String[], byte[], CommandResult> proc) {
		Objects.requireNonNull(argv0, "argv0");
		Objects.requireNonNull(proc, "proc");
		commands.put(argv0, proc);
	}

	@Override
	public CommandResult runCommand(String[] args, byte[] stdin) {
		if(args == null || args.length == 0) {
			throw new IllegalArgumentException();
		}

		BiFunction<String[], byte[], CommandResult> proc = commands.get(args[0]);
		if(proc == null) {
			return new CommandResult(args, 1, "", "");
		}

		return proc.apply(args, stdin);
	}

	@Override
	public void upload(String destPath, byte[] bytes, Set<PosixFilePermission> perms, Instant timestamp) throws IOException {
		Path dp = fs.getPath(destPath);
		Files.write(dp, bytes);
		Files.setPosixFilePermissions(dp, perms);
		Files.setLastModifiedTime(dp, FileTime.from(timestamp));
	}

	@Override
	public void close() {

	}

	private CommandResult env(String[] argv, byte[] stdin) {
		return new CommandResult(argv, 0, String.format("HOME=%s\n", home), "");
	}

	private CommandResult mkdir(String[] argv, byte[] stdin) {
		if(argv.length == 1) {
			return new CommandResult(argv, 1, "", "");
		}

		for(int i = 1; i < argv.length; ++i) {
			if("-p".equals(argv[i])) {
				continue;
			}

			/* In-memory filesystem, break it as much as you please. */
			try {
				Files.createDirectories(fs.getPath(argv[i]));
			} catch(IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return new CommandResult(argv, 0, "", "");
	}

	public static TestShellFactory createFactory(Path home) {
		return new TestShellFactory(home);
	}

	public static TransportFactory.Config createConfig() {
		return new TransportFactory.Config(
				Optional.empty(),
				new PublicKey[0],
				Optional.empty(),
				Optional.empty()
		);
	}

	private static class TestShellFactory implements TransportFactory {
		final Path home;

		TestShellFactory(Path home) {
			this.home = home;
		}

		@Override
		public RemoteShell create(Config cfg, Path workDir) {
			return new TestShell(home);
		}

		@Override
		public Optional<Config> validateConfiguration(JsonObject cfg, List<String> errors) {
			return Optional.of(createConfig());
		}

		@Override
		public Config resolveConfiguration(Config cfg) {
			return cfg;
		}

		@Override
		public JsonObject buildJsonConfiguration(Config cfg) {
			return JsonValue.EMPTY_JSON_OBJECT;
		}
	}
}