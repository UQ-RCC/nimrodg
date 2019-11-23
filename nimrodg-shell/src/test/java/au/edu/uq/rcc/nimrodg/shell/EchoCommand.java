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
package au.edu.uq.rcc.nimrodg.shell;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import org.apache.sshd.common.util.ValidateUtils;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.command.Command;

public class EchoCommand implements Command, Runnable {

	private final String command;
	private final String echoString;
	private InputStream in;
	private OutputStream out;
	private OutputStream err;
	private ExitCallback callback;

	public EchoCommand(String command, String[] argv) {
		this.command = ValidateUtils.checkNotNullAndNotEmpty(command, "No command");
		this.echoString = String.join(" ", Arrays.copyOfRange(argv, 1, argv.length)) + "\n";
	}

	public String getCommand() {
		return command;
	}

	@Override
	public void setInputStream(InputStream in) {
		this.in = in;
	}

	@Override
	public void setOutputStream(OutputStream out) {
		this.out = out;
	}

	@Override
	public void setErrorStream(OutputStream err) {
		this.err = err;
	}

	@Override
	public void setExitCallback(ExitCallback callback) {
		this.callback = callback;
	}

	@Override
	public void run() {
		try {
			try {
				out.write(this.echoString.getBytes(StandardCharsets.UTF_8));
			} finally {
				out.flush();
			}
		} catch(IOException e) {
			/* nop */
		}

		if(callback != null) {
			callback.onExit(0, "");
		}
	}

	@Override
	public void start(Environment env) {
		Thread thread = new Thread(this);
		thread.setDaemon(true);
		thread.start();
	}

	@Override
	public void destroy() {
		// ignored
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(getCommand());
	}

	@Override
	public boolean equals(Object obj) {
		if(obj == null) {
			return false;
		}
		if(obj == this) {
			return true;
		}
		if(getClass() != obj.getClass()) {
			return false;
		}

		return Objects.equals(this.getCommand(), ((EchoCommand)obj).getCommand());
	}

	@Override
	public String toString() {
		return command;
	}
}
