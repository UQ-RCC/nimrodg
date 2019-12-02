package au.edu.uq.rcc.nimrodg.resource;

import au.edu.uq.rcc.nimrodg.resource.ssh.TransportFactory;
import au.edu.uq.rcc.nimrodg.shell.RemoteShell;
import au.edu.uq.rcc.nimrodg.shell.ShellUtils;

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

	private final Path home;
	private final FileSystem fs;

	private TestShell(Path home) {
		this.home = home;
		this.fs = home.getFileSystem();
	}

	@Override
	public CommandResult runCommand(String[] args, byte[] stdin) throws IOException {
		if(args == null || args.length == 0) {
			throw new IllegalArgumentException();
		}

		String cmdline = ShellUtils.buildEscapedCommandLine(args);

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

	static TestShellFactory createFactory(Path home) {
		return new TestShellFactory(home);
	}

	static TransportFactory.Config createConfig() {
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