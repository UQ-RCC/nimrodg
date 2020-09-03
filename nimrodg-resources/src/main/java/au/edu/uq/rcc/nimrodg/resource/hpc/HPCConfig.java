package au.edu.uq.rcc.nimrodg.resource.hpc;

import au.edu.uq.rcc.nimrodg.resource.SSHResourceType;

import javax.json.JsonObjectBuilder;
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class HPCConfig extends SSHResourceType.SSHConfig {

	public final int limit;
	public final String tmpVar;
	public final int maxBatchSize;
	public final long ncpus;
	public final long mem;
	public final long walltime;
	public final long queryInterval;
	public final Optional<String> account;
	public final Optional<String> queue;
	public final Optional<String> server;
	public final HPCDefinition hpc;

	public HPCConfig(SSHResourceType.SSHConfig ssh, int limit, String tmpVar, int maxBatchSize, long ncpus, long mem, long walltime, long queryInterval, String account, String queue, String server, HPCDefinition hpc) {
		super(ssh);

		if((this.limit = limit) < 1) {
			throw new IllegalArgumentException("limit < 1");
		}

		this.tmpVar = Objects.requireNonNull(tmpVar, "tmpVar");

		if((this.maxBatchSize = maxBatchSize) < 1) {
			throw new IllegalArgumentException("maxBatchSize < 1");
		}

		if((this.ncpus = ncpus) < 1) {
			throw new IllegalArgumentException("ncpus < 1");
		}

		if((this.mem = mem) < 1) {
			throw new IllegalArgumentException("mem < 1");
		}

		if((this.walltime = walltime) < 1) {
			throw new IllegalArgumentException("walltime < 1");
		}

		if((this.queryInterval = queryInterval) < 1) {
			throw new IllegalArgumentException("queryInterval < 1");
		}

		this.account = Optional.ofNullable(account);
		this.queue = Optional.ofNullable(queue);
		this.server = Optional.ofNullable(server);
		this.hpc = Objects.requireNonNull(hpc, "hpc");
	}

	@Override
	protected JsonObjectBuilder toJsonBuilder() {
		JsonObjectBuilder job = super.toJsonBuilder()
				.add("limit", limit)
				.add("tmpvar", tmpVar)
				.add("max_batch_size", maxBatchSize)
				.add("definition", hpc.toJson())
				.add("ncpus", ncpus)
				.add("mem", mem)
				.add("walltime", walltime)
				.add("query_interval", queryInterval);

		account.ifPresent(a -> job.add("account", a));
		queue.ifPresent(q -> job.add("queue", q));
		server.ifPresent(s -> job.add("server", s));

		return job;
	}
}