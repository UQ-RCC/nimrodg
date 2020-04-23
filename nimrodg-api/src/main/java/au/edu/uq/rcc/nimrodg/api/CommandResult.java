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

public interface CommandResult {

	enum CommandResultStatus {
		PRECONDITION_FAILURE,
		SYSTEM_ERROR,
		EXCEPTION,
		ABORTED,
		FAILED,
		SUCCESS
	}

	CommandResultStatus getStatus();

	long getIndex();

	float getTime();

	int getReturnValue();

	String getMessage();

	int getErrorCode();

	boolean stopped();

	public static String statusToString(CommandResultStatus s) {
		switch(s) {
			case PRECONDITION_FAILURE:
				return "PRECONDITION_FAILURE";
			case SYSTEM_ERROR:
				return "SYSTEM_ERROR";
			case EXCEPTION:
				return "EXCEPTION";
			case ABORTED:
				return "ABORTED";
			case FAILED:
				return "FAILED";
			case SUCCESS:
				return "SUCCESS";
		}

		throw new IllegalArgumentException();
	}

	public static CommandResultStatus statusFromString(String s) {
		switch(s) {
			case "PRECONDITION_FAILURE":
				return CommandResultStatus.PRECONDITION_FAILURE;
			case "SYSTEM_ERROR":
				return CommandResultStatus.SYSTEM_ERROR;
			case "EXCEPTION":
				return CommandResultStatus.EXCEPTION;
			case "ABORTED":
				return CommandResultStatus.ABORTED;
			case "FAILED":
				return CommandResultStatus.FAILED;
			case "SUCCESS":
				return CommandResultStatus.SUCCESS;
		}

		throw new IllegalArgumentException();
	}
}
