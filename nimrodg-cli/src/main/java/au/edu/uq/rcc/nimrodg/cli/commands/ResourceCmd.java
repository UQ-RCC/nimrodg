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
package au.edu.uq.rcc.nimrodg.cli.commands;

import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodAPIException;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.ResourceType;
import au.edu.uq.rcc.nimrodg.setup.UserConfig;
import au.edu.uq.rcc.nimrodg.cli.CommandEntry;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLI;
import au.edu.uq.rcc.nimrodg.cli.NimrodCLICommand;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.json.Json;
import javax.json.JsonStructure;
import javax.json.JsonWriter;
import javax.json.stream.JsonGenerator;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;
import au.edu.uq.rcc.nimrodg.api.Resource;
import java.util.List;
import java.util.Optional;

public class ResourceCmd extends NimrodCLICommand {

	@Override
	public String getCommand() {
		return "resource";
	}

	private static NimrodURI getUri(Namespace args, String prefix) {
		String _uri = args.getString(String.format("%suri", prefix));
		return NimrodURI.create(
				_uri == null ? null : URI.create(_uri),
				args.getString(String.format("%scert", prefix)),
				args.getBoolean(String.format("%sno_verify_peer", prefix)),
				args.getBoolean(String.format("%sno_verify_host", prefix))
		);
	}

	@Override
	public int execute(Namespace args, UserConfig config, NimrodAPI nimrod, PrintStream out, PrintStream err) throws IOException, NimrodAPIException {

		/* NB: add-root and add-child are separate because they might use separate parsers. */
		switch(args.getString("operation")) {
			case "add":
				return executeAdd(nimrod, args, out, err);
			case "remove":
				return executeRemove(nimrod, args, out, err);
			case "list":
				return executeList(nimrod, args, out, err);
			case "query":
				return executeQuery(nimrod, args, out, err);
			case "assign":
				return executeAssign(nimrod, args, out, err);
			case "unassign":
				return executeUnassign(nimrod, args, out, err);
		}
		return 0;

	}

	private int executeAdd(NimrodAPI api, Namespace args, PrintStream out, PrintStream err) throws IOException, NimrodAPIException {
		String name = args.getString("resource_name");

		Resource node = api.getResource(name);
		if(node != null) {
			err.printf("Duplicate resource '%s'.\n", name);
			return 1;
		}

		String type = args.getString("type");
		if(type == null) {
			err.printf("No type specified.\n");
			return 1;
		}

		ResourceType rt;
		try {
			rt = api.getResourceTypeInfo(type);
			if(rt == null) {
				err.printf("No such resource type '%s'.\n", type);
				return 1;
			}
		} catch(NimrodAPIException e) {
			err.printf("Error instantiating resource type '%s'.\n", type);
			e.printStackTrace(err);
			return 1;
		}

		String[] resArgs = args.getList("args").toArray(new String[args.getList("args").size()]);

		JsonStructure cfg = rt.parseCommandArguments(api, resArgs, out, err);
		if(cfg == null) {
			return 1;
		}

		api.addResource(name, type, cfg, getUri(args, "amqp_"), getUri(args, "tx_"));
		out.printf("Successfully added resource '%s'.\n", name);
		return 0;
	}

	private int executeRemove(NimrodAPI api, Namespace args, PrintStream out, PrintStream err) throws IOException, NimrodAPIException {

		String path = args.getString("resource_name");

		Resource node = api.getResource(path);
		if(node == null) {
			err.printf("No such resource '%s'.\n", path);
			return 1;
		}

		api.deleteResource(node);
		out.printf("Resource '%s' removed successfully.", path);
		return 0;
	}

	private int executeList(NimrodAPI api, Namespace args, PrintStream out, PrintStream err) throws IOException, NimrodAPIException {
		Pattern p;
		try {
			p = Pattern.compile(args.getString("pathspec"));
		} catch(PatternSyntaxException e) {
			err.printf("Error compiling pattern.\n");
			e.printStackTrace(err);
			return 1;
		}

		api.getResources().forEach(n -> dumpResource(n, out, p));
		return 0;
	}

	private void dumpResource(Resource n, PrintStream out, Pattern pattern) {
		String path = n.getPath();
		if(pattern.matcher(path).matches()) {
			out.printf("%s\n", n.getPath());
		}
	}

	private int executeQuery(NimrodAPI api, Namespace args, PrintStream out, PrintStream err) throws IOException, NimrodAPIException {
		Resource n = api.getResource(args.get("resource_name"));

		if(n == null) {
			err.printf("No such resource.\n");
			return 0;
		}

		out.printf("Resource Information:\n");
		out.printf("  Name:   %s\n", n.getName());
		out.printf("  Type:   %s\n", n.getType().getName());

		out.printf("Config:");
		prettyPrint(n.getConfig(), out);
		return 0;
	}

	public static void prettyPrint(JsonStructure json, PrintStream ps) {
		Map<String, Boolean> ops = new HashMap<>();
		ops.put(JsonGenerator.PRETTY_PRINTING, true);

		try(JsonWriter w = Json.createWriterFactory(ops).createWriter(ps)) {
			w.write(json);
		}
	}

	private int executeAssign(NimrodAPI api, Namespace args, PrintStream out, PrintStream err) throws IOException, NimrodAPIException {
		String expName = args.getString("exp_name");

		Experiment exp = api.getExperiment(expName);
		if(exp == null) {
			err.printf("No such experiment '%s'.\n", expName);
			return 1;
		}

		Optional<NimrodURI> nuri = Optional.ofNullable(getUri(args, "tx_"));
		boolean failed = false;
		List<String> ress = args.getList("resource_name");
		for(String path : ress) {
			Resource node = api.getResource(path);
			if(node == null) {
				err.printf("No such resource '%s'.\n", path);
				failed = true;
				continue;
			}

			api.assignResource(node, exp, nuri);
		}

		return failed ? 1 : 0;
	}

	private int executeUnassign(NimrodAPI api, Namespace args, PrintStream out, PrintStream err) throws IOException, NimrodAPIException {

		String expName = args.getString("exp_name");
		Experiment exp = api.getExperiment(expName);
		if(exp == null) {
			err.printf("No such experiment '%s'.\n", expName);
			return 1;
		}

		boolean failed = false;
		List<String> ress = args.getList("resource_name");
		for(String path : ress) {
			Resource node = api.getResource(path);
			if(node == null) {
				err.printf("No such resource '%s'.\n", path);
				failed = true;
				continue;
			}

			api.unassignResource(node, exp);
		}
		return failed ? 1 : 0;
	}

	private static void addNameArgument(Subparser sp) {
		sp.addArgument("resource_name")
				.type(String.class)
				.help("The name of the resource")
				.required(true);
	}

	private static void addNameArgumentMultiple(Subparser sp) {
		sp.addArgument("resource_name")
				.type(String.class)
				.help("The name of the resource")
				.nargs("+")
				.required(true);
	}

	public static void main(String[] args) throws Exception {
		System.exit(NimrodCLI.cliMain(new String[]{"resource", "add-root", "local", "local"}));
	}

	public static final CommandEntry DEFINITION = new CommandEntry(new ResourceCmd(), "Compute resource operations.") {
		@Override
		public void addArgs(Subparser parser) {
			super.addArgs(parser);
			Subparsers subs = parser.addSubparsers()
					.dest("operation");

			{
				Subparser sp = subs.addParser("add")
						.help("Add a resource.")
						.description("Add a resource.");

				addNameArgument(sp);

				sp.addArgument("type")
						.type(String.class)
						.help("The type name of the resource")
						.required(true);

				addPrefixedUriArg(sp, "amqp", "AMQP", false);
				addPrefixedUriArg(sp, "tx", "Transfer", false);

				sp.addArgument("args")
						.nargs("*");
			}

			{
				Subparser sp = subs.addParser("remove")
						.help("Remove a resource node.")
						.description("Remove a resource node and all its children.");

				addNameArgument(sp);
			}

			{
				Subparser sp = subs.addParser("list")
						.help("List all resources.")
						.description("List all resources");

				sp.addArgument("pathspec")
						.type(String.class)
						.help("Path Specification (Java Regular Expressions)")
						.nargs("?")
						.setDefault(".*");
			}

			{
				Subparser sp = subs.addParser("query")
						.help("Query resource information.")
						.description("Query resource information.");

				addNameArgument(sp);
			}

			{
				Subparser sp = subs.addParser("assign")
						.help("Assign resource(s) to an experiment.")
						.description("Assign resource(s) to an experiment.");

				addNameArgumentMultiple(sp);
				addExpNameArg(sp);

				addPrefixedUriArg(sp, "tx", "Transfer", false);
			}

			{
				Subparser sp = subs.addParser("unassign")
						.help("Unassign a resource from an experiment.")
						.description("Unassign a resource from an experiment.");

				addNameArgumentMultiple(sp);
				addExpNameArg(sp);
			}

		}

	};
}
