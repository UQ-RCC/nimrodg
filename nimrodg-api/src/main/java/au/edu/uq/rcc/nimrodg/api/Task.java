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

import java.util.List;

public interface Task {

	public enum Name {
		NodeStart,
		Main
	}

	public Experiment getExperiment();

	public Name getName();

	public List<Command> getCommands();

	public static String taskNameToString(Task.Name name) {
		switch(name) {
			case NodeStart:
				return "nodestart";
			case Main:
				return "main";
		}

		throw new IllegalArgumentException();
	}

	public static Task.Name stringToTaskName(String s) {
		switch(s) {
			case "nodestart":
				return Task.Name.NodeStart;
			case "main":
				return Task.Name.Main;
		}

		throw new IllegalArgumentException();
	}
}
