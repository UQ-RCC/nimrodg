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

public interface CopyCommand extends Command {

	public enum Context {
		Root,
		Node
	}

	public Context getSourceContext();

	public CommandArgument getSourcePath();

	public Context getDestinationContext();

	public CommandArgument getDestinationPath();

	public static String contextToString(Context ctx) {
		switch(ctx) {
			case Node:
				return "node";
			case Root:
				return "root";
		}

		throw new IllegalArgumentException();
	}

	public static Context stringToContext(String s) {
		if(s == null) {
			throw new IllegalArgumentException();
		}

		switch(s) {
			case "node":
				return Context.Node;
			case "root":
				return Context.Root;
		}

		throw new IllegalArgumentException();
	}
}
