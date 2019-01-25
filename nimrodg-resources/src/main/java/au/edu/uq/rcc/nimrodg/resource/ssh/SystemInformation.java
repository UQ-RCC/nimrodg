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
package au.edu.uq.rcc.nimrodg.resource.ssh;

import java.io.IOException;
import org.apache.sshd.client.session.ClientSession;

/* http://pubs.opengroup.org/onlinepubs/9699919799/utilities/uname.html */
public class SystemInformation {

	public final String machine;
	public final String node;
	public final String kernelRelease;
	public final String kernelName;
	public final String kernelVersion;

	private SystemInformation(SSHClient.CommandResult m, SSHClient.CommandResult n, SSHClient.CommandResult r, SSHClient.CommandResult s, SSHClient.CommandResult v) {
		this.machine = m.stdout.trim();
		this.node = n.stdout.trim();
		this.kernelRelease = r.stdout.trim();
		this.kernelName = s.stdout.trim();
		this.kernelVersion = v.stdout.trim();
	}

	@Override
	public String toString() {
		/*
			"%s %s %s %s %s\n", <sysname>, <nodename>, <release>,
				<version>, <machine>
		 */
		return String.format("%s %s %s %s %s", kernelName, node, kernelRelease, kernelVersion, machine);
	}

	public static SystemInformation getSystemInformation(ClientSession ses) throws IOException {
		return new SystemInformation(
				SSHClient.runCommand(ses, "uname", "-m"),
				SSHClient.runCommand(ses, "uname", "-n"),
				SSHClient.runCommand(ses, "uname", "-r"),
				SSHClient.runCommand(ses, "uname", "-s"),
				SSHClient.runCommand(ses, "uname", "-v")
		);
	}

	public static SystemInformation getSystemInformation(RemoteShell shell) throws IOException {
		return new SystemInformation(
				shell.runCommand("uname", "-m"),
				shell.runCommand("uname", "-n"),
				shell.runCommand("uname", "-r"),
				shell.runCommand("uname", "-s"),
				shell.runCommand("uname", "-v")
		);
	}
}
