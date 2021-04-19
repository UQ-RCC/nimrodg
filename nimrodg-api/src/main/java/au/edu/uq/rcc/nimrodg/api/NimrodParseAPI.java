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
package au.edu.uq.rcc.nimrodg.api;

import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledTask;
import au.edu.uq.rcc.nimrodg.api.utils.run.RunBuilder;
import au.edu.uq.rcc.nimrodg.api.utils.run.VariableBuilder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This is an API as ANTLR4 has lots of runtime dependencies that shouldn't be in the global classpath.
 *
 * If using the {@code nimrodg-parsing} library, use {@code ANTLR4ParseAPIImpl#INSTANCE}
 */
public interface NimrodParseAPI {

	RunBuilder parseRunToBuilder(Reader r) throws IOException, PlanfileParseException;

	default RunBuilder parseRunToBuilder(InputStream is) throws IOException, PlanfileParseException {
		try(InputStreamReader isr = new InputStreamReader(is)) {
			return parseRunToBuilder(isr);
		}
	}

	default RunBuilder parseRunToBuilder(Path path) throws IOException, PlanfileParseException {
		try(BufferedReader r = Files.newBufferedReader(path)) {
			return parseRunToBuilder(r);
		}
	}

	default RunBuilder parseRunToBuilder(String s) throws PlanfileParseException {
		try(StringReader sr = new StringReader(s)) {
			return parseRunToBuilder(sr);
		} catch(IOException e) {
			throw new IllegalStateException(e);
		}
	}

	VariableBuilder[] parseVariableBlock(Reader r) throws IOException, PlanfileParseException;

	default VariableBuilder[] parseVariableBlock(InputStream is) throws IOException, PlanfileParseException {
		try(InputStreamReader isr = new InputStreamReader(is)) {
			return parseVariableBlock(isr);
		}
	}

	default VariableBuilder[] parseVariableBlock(Path path) throws IOException, PlanfileParseException {
		try(BufferedReader r = Files.newBufferedReader(path)) {
			return parseVariableBlock(r);
		}
	}

	default VariableBuilder[] parseVariableBlock(String s) throws PlanfileParseException {
		try(StringReader sr = new StringReader(s)) {
			return parseVariableBlock(sr);
		} catch(IOException e) {
			throw new IllegalStateException(e);
		}
	}

	CompiledTask parseTask(Reader r) throws IOException, PlanfileParseException;

	default CompiledTask parseTask(InputStream is) throws IOException, PlanfileParseException {
		try(InputStreamReader isr = new InputStreamReader(is)) {
			return parseTask(isr);
		}
	}

	default CompiledTask parseTask(Path path) throws IOException, PlanfileParseException {
		try(BufferedReader r = Files.newBufferedReader(path)) {
			return parseTask(r);
		}
	}

	default CompiledTask parseTask(String s) throws PlanfileParseException {
		try(StringReader sr = new StringReader(s)) {
			return parseTask(sr);
		} catch(IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
