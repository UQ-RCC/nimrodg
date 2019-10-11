/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2019 The University of Queensland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.edu.uq.rcc.nimrodg.resource.cluster;

import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.resource.HPCResourceType.HPCConfig;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import au.edu.uq.rcc.nimrodg.resource.ssh.RemoteShell;
import au.edu.uq.rcc.nimrodg.resource.ssh.SSHClient;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.interpret.JinjavaInterpreter;
import com.hubspot.jinjava.lib.filter.Filter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HPCActuator extends ClusterActuator<HPCConfig> {

	private static final Logger LOGGER = LogManager.getLogger(HPCActuator.class);
	private final Jinjava jj;
	private final Pattern jobRegex;

	public HPCActuator(Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, HPCConfig cfg) throws IOException {
		super(ops, node, amqpUri, certs, cfg);
		this.jj = createTemplateEngine();
		this.jobRegex = Pattern.compile(cfg.hpc.regex);
	}

	@Override
	protected String buildSubmissionScript(UUID batchUuid, UUID[] agentUuids, String out, String err) {
		Map<String, Object> agentVars = new HashMap<>();
		agentVars.put("amqp_uri", uri.uri);
		agentVars.put("amqp_routing_key", routingKey);
		agentVars.put("amqp_no_verify_peer", uri.noVerifyPeer);
		agentVars.put("amqp_no_verify_host", uri.noVerifyHost);
		this.remoteCertPath.ifPresent(p -> {
			agentVars.put("cacert", p);
			agentVars.put("caenc", "plain");
			agentVars.put("no_ca_delete", true);
		});
		agentVars.put("output", "workroot");
		agentVars.put("batch", false);

		Map<String, Object> vars = new HashMap<>();
		vars.put("batch_uuid", batchUuid);
		vars.put("batch_size", agentUuids.length);
		vars.put("batch_walltime", config.walltime);
		vars.put("output_path", out);
		vars.put("error_path", err);
		config.account.ifPresent(acc -> vars.put("job_account", acc));
		config.queue.ifPresent(q -> vars.put("job_queue", q));
		config.server.ifPresent(s -> vars.put("job_server", s));
		vars.put("job_ncpus", config.ncpus);
		vars.put("job_mem", config.mem);
		vars.put("agent_binary", this.remoteAgentPath);
		vars.put("agent_uuids", agentUuids);
		vars.put("agent_args", agentVars);
		return jj.render(config.hpc.template, vars);
	}

	@Override
	public void notifyAgentConnection(AgentState state) {
		if(isClosed) {
			return;
		}

		super.notifyAgentConnection(state);

		/* Set the walltime. */
		state.setExpiryTime(state.getConnectionTime().plusSeconds(config.walltime));
	}

	@Override
	protected String submitBatch(RemoteShell shell, TempBatch batch) throws IOException {
		String[] args = Stream.concat(Arrays.stream(config.hpc.submit), Stream.of(batch.scriptPath)).toArray(String[]::new);
		SSHClient.CommandResult qsub = shell.runCommand(args);
		if(qsub.status != 0) {
			throw new IOException("submission command failed");
		}

		/* Get the job ID. This will be the first line of stdout. It may also be empty. */
		String[] lines = qsub.stdout.split("[\r\n]", 2);
		String jobLine;
		if(lines.length >= 1) {
			jobLine = lines[0];
		} else {
			jobLine = "";
		}

		Matcher m = jobRegex.matcher(jobLine);
		if(!m.matches()) {
			throw new IOException("submission returned invalid or no job name");
		}

		if(!qsub.stderr.isEmpty()) {
			LOGGER.warn("Remote stderr not empty:");
			LOGGER.warn(qsub.stderr.trim());
		}

		return m.group(1);
	}

	@Override
	protected boolean killJobs(RemoteShell shell, String[] jobIds) {
		String[] args = Stream.concat(Arrays.stream(config.hpc.deleteForce), Stream.of(jobIds)).toArray(String[]::new);
		try {
			shell.runCommand(args);
		} catch(IOException ex) {
			LOGGER.warn("Unable to kill jobs '{}'", String.join(",", jobIds));
			LOGGER.catching(ex);
			return false;
		}
		return true;
	}

	public static Jinjava createTemplateEngine() {
		Jinjava jj = new Jinjava();
		jj.getGlobalContext().registerFilter(new Filter() {
			@Override
			public Object filter(Object o, JinjavaInterpreter ji, String... strings) {
				if(o == null) {
					return null;
				}
				return ActuatorUtils.posixQuoteArgument(o.toString());
			}

			@Override
			public String getName() {
				return "quote";
			}
		});
		return jj;
	}

	public static Map<String, Object> createSampleVars() {
		UUID[] uuids = {
			UUID.fromString("a0ffd1ac-db63-4c6e-b661-f9c9349afccd"),
			UUID.fromString("09814659-2674-4180-aa60-d7a2ebcc26fa"),
			UUID.fromString("3aa4bd16-d425-4171-ae6d-bc5294289d82"),
			UUID.fromString("63203256-fa7a-4ff0-b336-e0b3fea4808c"),
			UUID.fromString("dfe82a4b-e295-4128-9180-afd83eb1c756"),
			UUID.fromString("98028d6e-881b-4ae0-aff7-199a8dd26fea"),
			UUID.fromString("94e98f08-0a44-4693-8202-447377e58f65"),
			UUID.fromString("8752a071-4bac-4508-9881-49b70e2fa6ae"),
			UUID.fromString("c67de3a0-ec19-4780-b533-828999482e3a"),
			UUID.fromString("73f5c6e7-afca-466d-aba6-b85aec2c93da")
		};
		Map<String, Object> vars = new HashMap<>();
		vars.put("batch_uuid", "57ace1d4-0f8d-4439-9181-0fe91d6d73d4");
		vars.put("batch_size", uuids.length);
		vars.put("batch_walltime", 86400);
		vars.put("output_path", "/remote/path/to/stdout.txt");
		vars.put("error_path", "/remote/path/to/stderr.txt");
		vars.put("job_queue", "workq");
		vars.put("job_server", "tinmgr2.ib0");
		vars.put("job_account", "account");
		vars.put("job_ncpus", 12);
		vars.put("job_mem", 4294967296L);
		vars.put("agent_binary", "/remote/path/to/agent/binary");
		vars.put("agent_uuids", uuids);
		vars.put("agent_args", Map.of(
				"amqp_uri", "amqps://user:pass@host:port/vhost",
				"amqp_routing_key", "routingkey",
				"amqp_no_verify_peer", true,
				"amqp_no_verify_host", false,
				"cacert", "/path/to/cert.pem",
				"caenc", "b64",
				"no_ca_delete", true
		));
		return vars;
	}

	public static void main(String[] args) throws IOException {
		Jinjava jj = createTemplateEngine();
		Map<String, Object> vars = createSampleVars();

		String template = new String(HPCActuator.class.getResourceAsStream("hpc.pbspro.j2").readAllBytes(), StandardCharsets.UTF_8);
		String renderedTemplate = jj.render(template, vars);
		System.out.printf("%s", renderedTemplate);
	}
}
