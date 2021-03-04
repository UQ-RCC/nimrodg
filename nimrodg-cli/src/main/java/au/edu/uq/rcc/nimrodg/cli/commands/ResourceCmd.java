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
import au.edu.uq.rcc.nimrodg.api.NimrodException;
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
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.json.Json;
import javax.json.JsonStructure;
import javax.json.stream.JsonGenerator;

import com.inamik.text.tables.SimpleTable;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;
import au.edu.uq.rcc.nimrodg.api.Resource;
import java.nio.file.Path;
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
	public int execute(Namespace args, UserConfig config, NimrodAPI nimrod, PrintStream out, PrintStream err, Path[] configDirs) throws IOException, NimrodException {

		/* NB: add-root and add-child are separate because they might use separate parsers. */
		switch(args.getString("operation")) {
			case "add":
				return executeAdd(nimrod, args, out, err, configDirs);
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

	private int executeAdd(NimrodAPI api, Namespace args, PrintStream out, PrintStream err, Path[] configDirs) throws NimrodException {
		String name = args.getString("resource_name");

		Resource node = api.getResource(name);
		if(node != null) {
			err.printf("Duplicate resource '%s'.\n", name);
			return 1;
		}

		String type = args.getString("type");
		if(type == null) {
			err.print("No type specified.\n");
			return 1;
		}

		ResourceType rt;
		try {
			rt = api.getResourceTypeInfo(type);
			if(rt == null) {
				err.printf("No such resource type '%s'.\n", type);
				return 1;
			}
		} catch(NimrodException e) {
			err.printf("Error instantiating resource type '%s'.\n", type);
			e.printStackTrace(err);
			return 1;
		}

		String[] resArgs = args.getList("args").toArray(new String[args.getList("args").size()]);

		JsonStructure cfg = rt.parseCommandArguments(api, resArgs, out, err, configDirs);
		if(cfg == null) {
			return 1;
		}

		api.addResource(name, type, cfg, getUri(args, "amqp_"), getUri(args, "tx_"));
		return 0;
	}

	private int executeRemove(NimrodAPI api, Namespace args, PrintStream out, PrintStream err) throws NimrodException {
		args.getList("resource_name").stream()
				.distinct()
				.map(r -> api.getResource((String)r))
				.filter(Objects::nonNull)
				.forEach(api::deleteResource);
		return 0;
	}

	private int executeList(NimrodAPI api, Namespace args, PrintStream out, PrintStream err) throws NimrodException {
		Pattern p;
		try {
			p = Pattern.compile(args.getString("pathspec"));
		} catch(PatternSyntaxException e) {
			err.print("Error compiling pattern.\n");
			e.printStackTrace(err);
			return 1;
		}

		/* TODO: Print AMQP and TX Uris, stripping passwords */
		SimpleTable st = SimpleTable.of().nextRow()
				.nextCell("Name")
				.nextCell("Type")
				.nextCell("No. Agents");
		api.getResources().forEach(n -> dumpResource(api, n, st, p));
		printTable(st, out);
		return 0;
	}

	private void dumpResource(NimrodAPI api, Resource n, SimpleTable st, Pattern pattern) {
		String name = n.getName();
		if(!pattern.matcher(name).matches()) {
			return;
		}

		st.nextRow()
				.nextCell(n.getName())
				.nextCell(n.getTypeName())
				.nextCell(Integer.toString(api.getResourceAgents(n).size()));
	}

	private int executeQuery(NimrodAPI api, Namespace args, PrintStream out, PrintStream err) throws NimrodException {
		Resource n = api.getResource(args.get("resource_name"));

		if(n == null) {
			err.print("No such resource.\n");
			return 0;
		}

		out.print("Resource Information:\n");
		out.printf("  Name:   %s\n", n.getName());
		out.printf("  Type:   %s\n", n.getTypeName());

		out.print("Config:");
		prettyPrint(n.getConfig(), out);
		return 0;
	}

	private int executeAssign(NimrodAPI api, Namespace args, PrintStream out, PrintStream err) throws NimrodException {
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

	private int executeUnassign(NimrodAPI api, Namespace args, PrintStream out, PrintStream err) throws NimrodException {

		String expName = args.getString("exp_name");
		Experiment exp = api.getExperiment(expName);
		if(exp == null) {
			err.printf("No such experiment '%s'.\n", expName);
			return 1;
		}

		args.getList("resource_name").stream()
				.distinct()
				.map(r -> api.getResource((String)r))
				.filter(Objects::nonNull)
				.forEach(r -> api.unassignResource(r, exp));

		return 0;
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

	public static final CommandEntry DEFINITION = new CommandEntry(new ResourceCmd(), "Resource operations.") {
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
						.help("Remove resources.")
						.description("Remove one or more resources.");

				addNameArgumentMultiple(sp);
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
