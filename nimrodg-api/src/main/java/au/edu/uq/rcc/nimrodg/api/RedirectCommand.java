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

public interface RedirectCommand extends Command {

	public enum Stream {
		Stdout,
		Stderr
	}

	public Stream getStream();

	public boolean getAppend();

	public CommandArgument getFile();

	public static String streamToString(Stream s) {
		switch(s) {
			case Stdout:
				return "stdout";
			case Stderr:
				return "stderr";
		}

		throw new IllegalArgumentException();
	}

	public static Stream stringToStream(String s) {
		switch(s) {
			case "stdout":
				return Stream.Stdout;
			case "stderr":
				return Stream.Stderr;
		}

		throw new IllegalArgumentException();
	}
}
