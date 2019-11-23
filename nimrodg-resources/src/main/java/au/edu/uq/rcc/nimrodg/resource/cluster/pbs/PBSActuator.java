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
package au.edu.uq.rcc.nimrodg.resource.cluster.pbs;

import java.io.IOException;
import java.security.cert.Certificate;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import java.util.UUID;

import au.edu.uq.rcc.nimrodg.shell.RemoteShell;
import au.edu.uq.rcc.nimrodg.shell.ShellUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import au.edu.uq.rcc.nimrodg.api.Resource;
import au.edu.uq.rcc.nimrodg.resource.cluster.LegacyClusterActuator;
import au.edu.uq.rcc.nimrodg.resource.cluster.LegacyClusterResourceType.DialectConfig;
import au.edu.uq.rcc.nimrodg.shell.SshdClient;
import java.util.Arrays;
import java.util.stream.Stream;

public class PBSActuator extends LegacyClusterActuator {

	private static final Logger LOGGER = LogManager.getLogger(PBSActuator.class);

	public PBSActuator(Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs, DialectConfig cfg) throws IOException {
		super(ops, node, amqpUri, certs, cfg);
	}

	@Override
	protected void applyBatchedSubmissionArguments(StringBuilder sb, UUID[] uuids, String[] processedArgs, String out, String err) {
		sb.append(String.format("#PBS %s\n", String.join(" ", processedArgs)));
		sb.append(String.format("#PBS -o %s\n", ShellUtils.quoteArgument(out)));
		sb.append(String.format("#PBS -e %s\n\n", ShellUtils.quoteArgument(err)));
	}

	@Override
	protected String submitBatch(RemoteShell shell, TempBatch batch) throws IOException {
		SshdClient.CommandResult qsub = shell.runCommand("qsub", batch.scriptPath);
		if(qsub.status != 0) {
			throw new IOException("qsub command failed.");
		}

		/* Get the job ID. This will be the first line of stdout. It may also be empty. */
		String[] lines = qsub.stdout.split("[\r\n]", 2);
		String jobName = null;
		if(lines.length >= 1) {
			if(!lines[0].isEmpty()) {
				jobName = lines[0];
			}
		}

		if(!qsub.stderr.isEmpty()) {
			LOGGER.warn("Remote stderr not empty:");
			LOGGER.warn(qsub.stderr.trim());
		}

		/* Sometimes qsub "succeeds" with a 0 return code, but has actually failed. */
		if(jobName == null) {
			throw new IOException("qsub returned no job name.");
		}

		return jobName;
	}

	@Override
	protected boolean killJobs(RemoteShell shell, String[] jobIds) {
		String[] cmd = Stream.concat(Stream.of("qdel", "-W", "force"), Arrays.stream(jobIds)).toArray(String[]::new);
		try {
			shell.runCommand(cmd);
		} catch(IOException ex) {
			LOGGER.warn("Unable to execute qdel for jobs '{}'", String.join(",", jobIds));
			LOGGER.catching(ex);
			return false;
		}
		return true;
	}
}
