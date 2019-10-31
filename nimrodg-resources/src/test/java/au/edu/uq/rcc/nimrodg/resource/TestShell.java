package au.edu.uq.rcc.nimrodg.resource;

import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import au.edu.uq.rcc.nimrodg.resource.ssh.RemoteShell;
import au.edu.uq.rcc.nimrodg.resource.ssh.TransportFactory;

import javax.json.JsonObject;
import javax.json.JsonValue;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

public class TestShell implements RemoteShell {

	public final Path home;
	public final FileSystem fs;

	public TestShell(Path home) {
		this.home = home;
		this.fs = home.getFileSystem();
	}

	@Override
	public CommandResult runCommand(String... args) throws IOException {
		if(args == null || args.length == 0) {
			throw new IllegalArgumentException();
		}

		String cmdline = ActuatorUtils.posixBuildEscapedCommandLine(args);

		switch(args[0]) {
			case "env":
				return new CommandResult(cmdline, 0, String.format("HOME=%s\n", home), "");
			case "mkdir": {
				if(args.length == 1) {
					break;
				}

				for(int i = 1; i < args.length; ++i) {
					if("-p".equals(args[i])) {
						continue;
					}

					/* In-memory filesystem, break it as much as you please. */
					Files.createDirectories(fs.getPath(args[i]));
				}
				return new CommandResult(cmdline, 0, "", "");
			}
		}
		return new CommandResult(cmdline, 1, "", "");
	}

	@Override
	public void upload(String destPath, byte[] bytes, Collection<PosixFilePermission> perms, Instant timestamp) throws IOException {
		Path dp = fs.getPath(destPath);
		Files.write(dp, bytes);
		Files.setPosixFilePermissions(dp, new HashSet<>(perms));
		Files.setLastModifiedTime(dp, FileTime.from(timestamp));
	}

	@Override
	public void upload(String destPath, Path path, Collection<PosixFilePermission> perms, Instant timestamp) throws IOException {
		Path dp = fs.getPath(destPath);
		Files.copy(fs.getPath(path.toString()), dp);
		Files.setPosixFilePermissions(dp, new HashSet<>(perms));
		Files.setLastModifiedTime(dp, FileTime.from(timestamp));
	}

	@Override
	public void close() {

	}

	public static TestShellFactory createFactory(Path home) {
		return new TestShellFactory(home);
	}

	public static TransportFactory.Config createConfig() {
		return new TransportFactory.Config(
				Optional.empty(),
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
		public RemoteShell create(Config cfg, Path workDir) throws IOException {
			return new TestShell(home);
		}

		@Override
		public Optional<Config> validateConfiguration(JsonObject cfg, List<String> errors) {
			return Optional.of(createConfig());
		}

		@Override
		public Config resolveConfiguration(Config cfg) throws IOException {
			return cfg;
		}

		@Override
		public JsonObject buildJsonConfiguration(Config cfg) {
			return JsonValue.EMPTY_JSON_OBJECT;
		}
	}
}