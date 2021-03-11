package au.edu.uq.rcc.nimrodg.impl.base.db;

import au.edu.uq.rcc.nimrodg.api.setup.SchemaVersion;

import java.net.URI;
import java.util.Objects;

public final class UpgradeStep {
	public final SchemaVersion from;
	public final SchemaVersion to;
	public final URI migrationScript;

	private UpgradeStep(SchemaVersion from, SchemaVersion to, URI migrationScript) {
		this.from = Objects.requireNonNull(from, "from");
		this.to = Objects.requireNonNull(to, "to");
		this.migrationScript = Objects.requireNonNull(migrationScript, "migrationScript");
	}

	public static UpgradeStep of(SchemaVersion from, SchemaVersion to, URI migrationScript) {
		return new UpgradeStep(from, to, migrationScript);
	}

}
