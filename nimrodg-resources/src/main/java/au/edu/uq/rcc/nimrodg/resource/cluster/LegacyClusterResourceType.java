package au.edu.uq.rcc.nimrodg.resource.cluster;

import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.AgentProvider;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public abstract class LegacyClusterResourceType extends ClusterResourceType {

	protected static final Pattern BATCH_RESOURCE_PATTERN = Pattern.compile("^([\\w-]+):(.+)$");

	protected final BatchDialect dialect;

	protected LegacyClusterResourceType(String name, String displayName, String argsName, BatchDialect dialect) {
		super(name, displayName, argsName);
		this.dialect = dialect;
	}

	@Override
	protected void buildParserBeforeSubmissionArgs(ArgumentParser argparser) {
		argparser.addArgument("--add-batch-res-static")
				.dest("batch_resource_static")
				.type(String.class)
				.action(Arguments.append())
				.help("Add a static batch resource.");

		argparser.addArgument("--add-batch-res-scale")
				.dest("batch_resource_scale")
				.type(String.class)
				.action(Arguments.append())
				.help("Add a scalable batch resource.");
	}

	private static boolean parseBatchResource(String s, PrintStream err, List<BatchDialect.Resource> res) {
		Matcher m = BATCH_RESOURCE_PATTERN.matcher(s);
		if(!m.matches()) {
			err.printf("Malformed batch static resource specification. Must match pattern %s\n", BATCH_RESOURCE_PATTERN.pattern());
			return false;
		}

		res.add(new BatchDialect.Resource(m.group(1), m.group(2)));
		return true;
	}

	@Override
	protected boolean parseArguments(AgentProvider ap, Namespace ns, PrintStream out, PrintStream err, Path[] configDirs, JsonObjectBuilder jb) {
		boolean valid = super.parseArguments(ap, ns, out, err, configDirs, jb);

		List<BatchDialect.Resource> staticResources = new ArrayList<>();
		List<String> resList = ns.getList("batch_resource_static");
		if(resList != null) {
			for(String s : resList) {
				valid = parseBatchResource(s, err, staticResources) && valid;
			}
		}

		List<BatchDialect.Resource> scaleResources = new ArrayList<>();
		resList = ns.getList("batch_resource_scale");
		if(resList != null) {
			for(String s : resList) {
				valid = parseBatchResource(s, err, scaleResources) && valid;
			}
		}

		JsonArrayBuilder jao = Json.createArrayBuilder();
		valid = dialect.parseResources(
				scaleResources.stream().toArray(BatchDialect.Resource[]::new),
				staticResources.stream().toArray(BatchDialect.Resource[]::new),
				out,
				err,
				jao
		) && valid;

		jb.add("batch_config", jao);
		return valid;
	}

	@Override
	protected String getConfigSchema() {
		return "resource_cluster_legacy.json";
	}

	@Override
	protected final Actuator createActuator(Actuator.Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, ClusterConfig ccfg) throws IOException {
		JsonObject cfg = node.getConfig().asJsonObject();
		return createActuator(ops, node, amqpUri, certs, new DialectConfig(
				ccfg,
				dialect,
				cfg.getJsonArray("batch_config").stream().map(v -> v.asJsonObject()).toArray(JsonObject[]::new)
		));
	}

	protected abstract Actuator createActuator(Actuator.Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, DialectConfig ccfg) throws IOException;

	public static class DialectConfig extends ClusterConfig {

		public final BatchDialect dialect;
		public final JsonObject[] batchConfig;

		public DialectConfig(ClusterConfig cfg, BatchDialect dialect, JsonObject[] batchConfig) {
			super(cfg);
			this.dialect = dialect;
			this.batchConfig = Arrays.copyOf(batchConfig, batchConfig.length);
		}

		public DialectConfig(DialectConfig cfg) {
			this(cfg, cfg.dialect, cfg.batchConfig);
		}
	}
}
