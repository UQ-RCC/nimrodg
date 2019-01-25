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
package au.edu.uq.rcc.nimrodg.cli;

import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodAPIException;
import au.edu.uq.rcc.nimrodg.api.NimrodAPIFactory;
import au.edu.uq.rcc.nimrodg.setup.UserConfig;
import java.io.IOException;
import java.io.PrintStream;
import net.sourceforge.argparse4j.inf.Namespace;

/**
 * A Nimrod CLI command. Use this for commands that require an instance of the {@link NimrodAPI}. It saves on the
 * boilerplate.
 */
public abstract class NimrodCLICommand extends DefaultCLICommand {

	@Override
	public abstract String getCommand();

	@Override
	public final int execute(Namespace args, UserConfig config, PrintStream out, PrintStream err) throws Exception {
		try(NimrodAPI napi = createFactory(config).createNimrod(config)) {
			return execute(args, config, napi, out, err);
		}
	}

	public abstract int execute(Namespace args, UserConfig config, NimrodAPI nimrod, PrintStream out, PrintStream err) throws IOException, NimrodAPIException;

	public static NimrodAPIFactory createFactory(UserConfig config) throws ReflectiveOperationException {
		return createFactory(config.factory());
	}

	public static NimrodAPIFactory createFactory(String factory) throws ReflectiveOperationException {
		Class<?> clazz = Class.forName(factory);

		if(!NimrodAPIFactory.class.isAssignableFrom(clazz)) {
			throw new IllegalArgumentException(String.format("API Factory is not actually a factory."));
		}

		return (NimrodAPIFactory)clazz.getConstructor().newInstance();
	}
}
