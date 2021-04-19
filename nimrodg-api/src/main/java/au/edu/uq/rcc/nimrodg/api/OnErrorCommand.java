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

public interface OnErrorCommand extends Command {

	enum Action {
		Fail,
		Ignore
	}

	Action getAction();

	static String actionToString(Action a) {
		switch(a) {
			case Fail:
				return "fail";
			case Ignore:
				return "ignore";
		}

		throw new IllegalArgumentException();
	}

	static Action stringToAction(String s) {
		if(s == null) {
			throw new IllegalArgumentException();
		}

		switch(s) {
			case "fail":
				return Action.Fail;
			case "ignore":
				return Action.Ignore;
		}

		throw new IllegalArgumentException();
	}
}
