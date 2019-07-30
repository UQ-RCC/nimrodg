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
package au.edu.uq.rcc.nimrodg.resource;

import au.edu.uq.rcc.nimrodg.api.AgentProvider;
import au.edu.uq.rcc.nimrodg.api.MasterResourceType;
import au.edu.uq.rcc.nimrodg.resource.act.ActuatorUtils;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonStructure;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

/**
 * This is nice boilerplate for resource argument and configuration handling.
 */
public abstract class BaseResourceType implements MasterResourceType {

	@Override
	public final JsonStructure parseCommandArguments(AgentProvider ap, String[] args, PrintStream out, PrintStream err, Path[] configDirs) {
		ArgumentParser parser = createParseHeader();
		addArguments(parser);

		Namespace ns;
		try {
			ns = parser.parseArgs(args);
		} catch(ArgumentParserException e) {
			parser.handleError(e);
			return null;
		}

		JsonObjectBuilder cfg = Json.createObjectBuilder();
		boolean valid = parseArguments(ap, ns, out, err, cfg);
		if(!valid) {
			return null;
		}

		JsonObject bcfg = cfg.build();
		List<String> errors = new ArrayList<>();
		if(!validateConfiguration(ap, bcfg, errors)) {
			errors.forEach(err::println);
			return null;
		}

		return bcfg;
	}

	protected ArgumentParser createParseHeader() {
		ArgumentParser argparser = ArgumentParsers.newArgumentParser(this.getName())
				.defaultHelp(true)
				.description(String.format("%s resource configuration", this.getDisplayName()));
		return argparser;
	}

	protected void addArguments(ArgumentParser parser) {

	}

	/**
	 * Parse arguments for a resource.
	 *
	 * @param ap The agent provider instance used to resolve agents.
	 * @param ns The argument namespace.
	 * @param out Output Stream.
	 * @param err Error Stream.
	 * @param jb The JSON object to add arguments to.
	 * @return If the arguments were parsed successfully, return true. Otherwise false.
	 */
	protected boolean parseArguments(AgentProvider ap, Namespace ns, PrintStream out, PrintStream err, JsonObjectBuilder jb) {
		return true;
	}

	/**
	 * Validate a resource configuration blob.
	 *
	 * @param ap The agent provider instance used to validate agents.
	 * @param _cfg The JSON blob to validate.
	 * @param errors A list of errors.
	 * @return If validated successfully, return true. Otherwise, false.
	 */
	protected boolean validateConfiguration(AgentProvider ap, JsonStructure _cfg, List<String> errors) {
		String schema = this.getConfigSchema();
		if(schema == null) {
			errors.add("No schema provided.");
			return false;
		}

		return ActuatorUtils.validateAgainstSchema(schema, this::getSchemaByPath, _cfg, errors);
	}

	@Override
	public abstract String getName();

	public abstract String getDisplayName();

	protected abstract String getConfigSchema();

	protected InputStream getSchemaByPath(String path) {
		return BaseResourceType.class.getResourceAsStream("/au/edu/uq/rcc/nimrodg/resource/" + path);
	}
}
