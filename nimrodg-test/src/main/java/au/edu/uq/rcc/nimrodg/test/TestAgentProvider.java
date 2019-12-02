package au.edu.uq.rcc.nimrodg.test;

import au.edu.uq.rcc.nimrodg.api.AgentInfo;
import au.edu.uq.rcc.nimrodg.api.AgentProvider;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public class TestAgentProvider implements AgentProvider {
	public final Path path;

	public TestAgentProvider(Path path) {
		this.path = path;
	}

	@Override
	public Map<String, AgentInfo> lookupAgents() {
		return Map.of(
				"x86_64-pc-linux-musl", new AgentInfo() {
					@Override
					public String getPlatformString() {
						return "x86_64-pc-linux-musl";
					}

					@Override
					public String getPath() {
						return path.resolve(".agents").resolve("x86_64-pc-linux-musl").toString();
					}

					@Override
					public Set<Map.Entry<String, String>> posixMappings() {
						return Set.of(
								Map.entry("Linux", "x86_64"),
								Map.entry("Linux", "k10m")
						);
					}
				},
				"i686-pc-linux-musl", new AgentInfo() {
					@Override
					public String getPlatformString() {
						return "i686-pc-linux-musl";
					}

					@Override
					public String getPath() {
						return path.resolve(".agents").resolve("i686-pc-linux-musl").toString();
					}

					@Override
					public Set<Map.Entry<String, String>> posixMappings() {
						return Set.of(Map.entry("Linux", "i686"));
					}
				},
				"noop", new AgentInfo() {
					@Override
					public String getPlatformString() {
						return "noop";
					}

					@Override
					public String getPath() {
						return "/bin/true";
					}

					@Override
					public Set<Map.Entry<String, String>> posixMappings() {
						return Set.of();
					}
				}
		);
	}

	@Override
	public AgentInfo lookupAgentByPlatform(String platString) {
		return this.lookupAgents().get(platString);
	}

	@Override
	public AgentInfo lookupAgentByPosix(String system, String machine) {
		return this.lookupAgents().values().stream()
				.filter(ai -> ai.posixMappings().contains(Map.entry(system, machine)))
				.findFirst()
				.orElse(null);
	}
}