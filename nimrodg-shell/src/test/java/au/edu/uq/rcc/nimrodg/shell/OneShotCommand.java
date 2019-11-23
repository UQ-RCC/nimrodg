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
import java.nio.file.FileSystem;
import java.util.Arrays;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.command.Command;

public class OneShotCommand implements Command {

	private InputStream stdin;
	private OutputStream stdout;
	private OutputStream stderr;
	private ExitCallback onExit;

	private final String command;
	private final FileSystem fs;

	public OneShotCommand(String command, FileSystem fs) {
		this.command = command;
		this.fs = fs;
	}

	@Override
	public void setInputStream(InputStream stdin) {
		this.stdin = stdin;
	}

	@Override
	public void setOutputStream(OutputStream stdout) {
		this.stdout = stdout;
	}

	@Override
	public void setErrorStream(OutputStream stderr) {
		this.stderr = stderr;
	}

	@Override
	public void setExitCallback(ExitCallback onExit) {
		this.onExit = onExit;
	}

	private int execCommand(String[] argv) throws IOException {
		if(argv.length == 0) {
			return 0;
		}

		if("exit".equals(argv[0])) {
			return 0;
		} else if("printf".equals(argv[0])) {
			return printf(argv);
		} else if("echo".equals(argv[0])) {
			writeFormatted(stdout, "%s\n", rangeJoin(" ", 1, argv));
			stdout.flush();
			return 0;
		} else {
			writeFormatted(stderr, "Unknown command: %s\n", rangeJoin(" ", 0, argv));
			stderr.flush();
			return 1;
		}
	}

	private int printf(String[] argv) throws IOException {
		if(argv.length < 3) {
			writeFormatted(stderr, "Not enough arguments.\n");
			stderr.flush();
			return 2;
		}
		/* TODO: Unescape this format string properly. */
		writeFormatted(stdout, argv[1].replace("\\\\n", "\n"), (Object[])Arrays.copyOfRange(argv, 2, argv.length));
		stdout.flush();
		return 0;
	}

	private static String rangeJoin(CharSequence delimiter, int start, CharSequence... elements) {
		return String.join(delimiter, Arrays.copyOfRange(elements, start, elements.length));
	}

	private static void writeFormatted(OutputStream os, String fmt, Object... args) throws IOException {
		os.write(String.format(fmt, (Object[])args).getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public void start(Environment arg0) throws IOException {
		String[] argv = ShellUtils.translateCommandline(this.command);
		int ret = execCommand(argv);
		if(onExit != null) {
			onExit.onExit(ret);
		}
	}

	@Override
	public void destroy() {

	}
}
