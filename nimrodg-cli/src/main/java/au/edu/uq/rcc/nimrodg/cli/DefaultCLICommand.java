/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2021 The University of Queensland
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
package au.edu.uq.rcc.nimrodg.cli;

import au.edu.uq.rcc.nimrodg.api.setup.UserConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import com.inamik.text.tables.GridTable;
import com.inamik.text.tables.SimpleTable;
import com.inamik.text.tables.grid.Border;
import com.inamik.text.tables.grid.Util;
import net.sourceforge.argparse4j.inf.Namespace;
import org.ini4j.Ini;

import javax.json.Json;
import javax.json.JsonStructure;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;

public abstract class DefaultCLICommand implements CLICommand {

	@Override
	public int execute(Namespace args, PrintStream out, PrintStream err, Path[] configDirs) throws Exception {
		return execute(args, loadConfigFile(Paths.get(args.getString("config"))), out, err, configDirs);
	}

	public abstract int execute(Namespace args, UserConfig cfg, PrintStream out, PrintStream err, Path[] configDirs) throws Exception;

	public static IniUserConfig loadConfigFile(Path configFile) throws IOException {
		/* Load the configuration */
		if(!Files.exists(configFile)) {
			throw new IOException(String.format("Configuration file %s doesn't exist", configFile));
		}

		Ini ini = new Ini();
		try(InputStream is = Files.newInputStream(configFile)) {
			ini.load(is);
		}

		return new IniUserConfig(ini, configFile);
	}

	private static final JsonWriterFactory JSON_WRITER_FACTORY = Json.createWriterFactory(
			Map.of(JsonGenerator.PRETTY_PRINTING, true)
	);

	public static void prettyPrint(JsonStructure json, PrintStream ps) {
		JSON_WRITER_FACTORY.createWriter(ps).write(json);
		ps.println();
	}

	public static void printTable(GridTable g, PrintStream ps) {
		ps.print(Util.asString(Border.of(Border.Chars.of('+', '-', '|')).apply(g)));
	}

	public static void printTable(SimpleTable st, PrintStream ps) {
		printTable(st.toGrid(), ps);
	}
}
