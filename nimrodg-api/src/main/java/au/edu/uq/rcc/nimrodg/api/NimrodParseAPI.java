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
import java.util.Collection;

/**
 * This is an API as ANTLR4 has lots of runtime dependencies that shouldn't be in the global classpath.
 *
 * If using the {@code nimrodg-parsing} library, use {@code ANTLR4ParseAPIImpl#INSTANCE}
 */
public interface NimrodParseAPI {

	RunBuilder parseRunToBuilder(Reader r, Collection<String> errors) throws IOException, PlanfileParseException;

	default RunBuilder parseRunToBuilder(InputStream is, Collection<String> errors) throws IOException, PlanfileParseException {
		try(InputStreamReader isr = new InputStreamReader(is)) {
			return parseRunToBuilder(isr, errors);
		}
	}

	default RunBuilder parseRunToBuilder(Path path, Collection<String> errors) throws IOException, PlanfileParseException {
		try(BufferedReader r = Files.newBufferedReader(path)) {
			return parseRunToBuilder(r, errors);
		}
	}

	default RunBuilder parseRunToBuilder(String s, Collection<String> errors) throws PlanfileParseException {
		try(StringReader sr = new StringReader(s)) {
			return parseRunToBuilder(sr, errors);
		} catch(IOException e) {
			throw new IllegalStateException("StringReader threw IOException", e);
		}
	}

	VariableBuilder[] parseVariableBlock(Reader r, Collection<String> errors) throws IOException, PlanfileParseException;

	default VariableBuilder[] parseVariableBlock(InputStream is, Collection<String> errors) throws IOException, PlanfileParseException {
		try(InputStreamReader isr = new InputStreamReader(is)) {
			return parseVariableBlock(isr, errors);
		}
	}

	default VariableBuilder[] parseVariableBlock(Path path, Collection<String> errors) throws IOException, PlanfileParseException {
		try(BufferedReader r = Files.newBufferedReader(path)) {
			return parseVariableBlock(r, errors);
		}
	}

	default VariableBuilder[] parseVariableBlock(String s, Collection<String> errors) throws PlanfileParseException {
		try(StringReader sr = new StringReader(s)) {
			return parseVariableBlock(sr, errors);
		} catch(IOException e) {
			throw new IllegalStateException("StringReader threw IOException", e);
		}
	}

	CompiledTask parseTask(Reader r, Collection<String> errors) throws IOException, PlanfileParseException;

	default CompiledTask parseTask(InputStream is, Collection<String> errors) throws IOException, PlanfileParseException {
		try(InputStreamReader isr = new InputStreamReader(is)) {
			return parseTask(isr, errors);
		}
	}

	default CompiledTask parseTask(Path path, Collection<String> errors) throws IOException, PlanfileParseException {
		try(BufferedReader r = Files.newBufferedReader(path)) {
			return parseTask(r, errors);
		}
	}

	default CompiledTask parseTask(String s, Collection<String> errors) throws PlanfileParseException {
		try(StringReader sr = new StringReader(s)) {
			return parseTask(sr, errors);
		} catch(IOException e) {
			throw new IllegalStateException("StringReader threw IOException", e);
		}
	}
}
