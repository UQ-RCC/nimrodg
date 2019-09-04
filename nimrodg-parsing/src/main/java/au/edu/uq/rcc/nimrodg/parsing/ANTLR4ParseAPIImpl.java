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
package au.edu.uq.rcc.nimrodg.parsing;

import au.edu.uq.rcc.nimrodg.api.NimrodParseAPI;
import au.edu.uq.rcc.nimrodg.api.PlanfileParseException;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledTask;
import au.edu.uq.rcc.nimrodg.api.utils.run.RunBuilder;
import au.edu.uq.rcc.nimrodg.api.utils.run.VariableBuilder;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileLexer;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParser;
import au.edu.uq.rcc.nimrodg.parsing.visitors.NimrodFileVisitor;
import au.edu.uq.rcc.nimrodg.parsing.visitors.TaskVisitor;
import au.edu.uq.rcc.nimrodg.parsing.visitors.VariableBlockVisiter;
import java.io.IOException;
import java.io.Reader;
import java.util.function.Function;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public class ANTLR4ParseAPIImpl implements NimrodParseAPI {

	private static <T> T parseOrThrow(Reader r, Function<NimrodFileParser, T> sss) throws IOException {
		NimrodFileParser parser = new NimrodFileParser(
				new CommonTokenStream(new NimrodFileLexer(CharStreams.fromReader(r)))
		);
		parser.removeErrorListeners();

		PlanfileParseException exc = new PlanfileParseException();
		parser.addErrorListener(new UselessErrorListener(exc));

		T t = sss.apply(parser);
		if(!exc.getErrors().isEmpty()) {
			throw exc;
		}
		return t;
	}

	@Override
	public RunBuilder parseRunToBuilder(Reader r) throws IOException, PlanfileParseException {
		return parseOrThrow(r, p -> p.nimrodFile().accept(NimrodFileVisitor.INSTANCE));
	}

	@Override
	public VariableBuilder[] parseVariableBlock(Reader r) throws IOException, PlanfileParseException {
		return parseOrThrow(r, p -> p.variableBlock().accept(VariableBlockVisiter.INSTANCE).stream().toArray(VariableBuilder[]::new));
	}

	@Override
	public CompiledTask parseTask(Reader r) throws IOException, PlanfileParseException {
		return parseOrThrow(r, p -> p.taskBlock().accept(TaskVisitor.INSTANCE));
	}

	public static final NimrodParseAPI INSTANCE = new ANTLR4ParseAPIImpl();
}
