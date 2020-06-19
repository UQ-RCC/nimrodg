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
package au.edu.uq.rcc.nimrodg.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NetworkJob {

	public interface ResolvedCommand {
		Command.Type getType();
	}

	public static final class OnErrorCommand implements ResolvedCommand {

		public final au.edu.uq.rcc.nimrodg.api.OnErrorCommand.Action action;

		public OnErrorCommand(au.edu.uq.rcc.nimrodg.api.OnErrorCommand.Action action) {
			this.action = action;
		}

		@Override
		public Command.Type getType() {
			return Command.Type.OnError;
		}
	}

	public static final class RedirectCommand implements ResolvedCommand {

		public final au.edu.uq.rcc.nimrodg.api.RedirectCommand.Stream stream;
		public final boolean append;
		public final String file;

		public RedirectCommand(au.edu.uq.rcc.nimrodg.api.RedirectCommand.Stream stream, boolean append, String file) {
			this.stream = stream;
			this.append = append;
			this.file = file;
		}

		@Override
		public Command.Type getType() {
			return Command.Type.Redirect;
		}

	}

	public static final class CopyCommand implements ResolvedCommand {

		public final au.edu.uq.rcc.nimrodg.api.CopyCommand.Context sourceContext;
		public final String sourcePath;

		public final au.edu.uq.rcc.nimrodg.api.CopyCommand.Context destinationContext;
		public final String destinationPath;

		public CopyCommand(au.edu.uq.rcc.nimrodg.api.CopyCommand.Context srcCtx, String srcPath, au.edu.uq.rcc.nimrodg.api.CopyCommand.Context dstCtx, String dstPath) {
			this.sourceContext = srcCtx;
			this.sourcePath = srcPath;

			this.destinationContext = dstCtx;
			this.destinationPath = dstPath;
		}

		@Override
		public Command.Type getType() {
			return Command.Type.Copy;
		}

	}

	public static final class ExecCommand implements ResolvedCommand {

		public final String program;
		public final List<String> arguments;
		public final boolean searchPath;

		public ExecCommand(String program, List<String> arguments, boolean searchPath) {
			this.program = program;
			this.arguments = List.copyOf(arguments);
			this.searchPath = searchPath;
		}

		@Override
		public Command.Type getType() {
			return Command.Type.Exec;
		}
	}

	public final UUID uuid;
	public final long index;
	public final String txUri;
	public final String token;
	public final List<ResolvedCommand> commands;
	public final Map<String, String> environment;
	public final int numCommands;

	public NetworkJob(UUID uuid, long index, String txuri, String token, List<ResolvedCommand> commands, Map<String, String> env) {
		this.uuid = uuid;
		this.index = index;
		this.txUri = txuri;
		this.token = token;
		this.commands = List.copyOf(commands);
		this.environment = Map.copyOf(env);
		this.numCommands = commands.size();
	}
}
