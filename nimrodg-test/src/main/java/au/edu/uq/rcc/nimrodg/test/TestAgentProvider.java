package au.edu.uq.rcc.nimrodg.test;

import au.edu.uq.rcc.nimrodg.api.AgentDefinition;
import au.edu.uq.rcc.nimrodg.api.AgentProvider;
import au.edu.uq.rcc.nimrodg.api.MachinePair;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public class TestAgentProvider implements AgentProvider {
	public final Path path;

	public TestAgentProvider(Path path) {
		this.path = path;
	}

	@Override
	public Map<String, AgentDefinition> lookupAgents() {
		return Map.of(
				"x86_64-pc-linux-musl", new AgentDefinition() {
					@Override
					public String getPlatformString() {
						return "x86_64-pc-linux-musl";
					}

					@Override
					public Path getPath() {
						return path.resolve(".agents").resolve("x86_64-pc-linux-musl");
					}

					@Override
					public Set<MachinePair> posixMappings() {
						return Set.of(
								MachinePair.of("Linux", "x86_64"),
								MachinePair.of("Linux", "k10m")
						);
					}
				},
				"i686-pc-linux-musl", new AgentDefinition() {
					@Override
					public String getPlatformString() {
						return "i686-pc-linux-musl";
					}

					@Override
					public Path getPath() {
						return path.resolve(".agents").resolve("i686-pc-linux-musl");
					}

					@Override
					public Set<MachinePair> posixMappings() {
						return Set.of(MachinePair.of("Linux", "i686"));
					}
				},
				"noop", new AgentDefinition() {
					@Override
					public String getPlatformString() {
						return "noop";
					}

					@Override
					public Path getPath() {
						return path.resolve("bin").resolve("true");
					}

					@Override
					public Set<MachinePair> posixMappings() {
						return Set.of();
					}
				}
		);
	}

	@Override
	public AgentDefinition lookupAgentByPlatform(String platString) {
		return this.lookupAgents().get(platString);
	}

	@Override
	public AgentDefinition lookupAgentByPosix(MachinePair pair) {
		return this.lookupAgents().values().stream()
				.filter(ai -> ai.posixMappings().contains(pair))
				.findFirst()
				.orElse(null);
	}
}
