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
package au.edu.uq.rcc.nimrodg.test;

import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.NimrodParseAPI;
import au.edu.uq.rcc.nimrodg.api.PlanfileParseException;
import au.edu.uq.rcc.nimrodg.api.utils.SubstitutionException;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;
import au.edu.uq.rcc.nimrodg.api.utils.run.RunBuilder;
import au.edu.uq.rcc.nimrodg.parsing.ANTLR4ParseAPIImpl;
import java.io.IOException;
import java.util.ArrayList;
import javax.json.JsonValue;

public class TestUtils {

	public static final NimrodParseAPI PARSE_API = ANTLR4ParseAPIImpl.INSTANCE;

	public static CompiledRun getSampleExperiment() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException, PlanfileParseException {
		return PARSE_API.parseRunToBuilder(
				"variable x index 0 list \"value-x-0\" \"value-x-1\" \"2\" \"a\" \"za\" \"sf\" \"a\" \"s\" \"a\" \"sdfa\" \"sd\" \"asdfasd\" \"\\x20\"\n"
				+ "variable y index 1 list \"value-y-0\" \"value-y-1\" \"10\"\n"
				+ "\n"
				+ "jobs\n"
				+ "	0001 0 0\n"
				+ "	0002 1 0\n"
				+ "endjobs\n"
				+ "task main\n"
				+ "	// Line comment test\n"
				+ "	redirect stdout to stdout.$x.$y.txt\n"
				+ "	redirect stderr off\n"
				+ "	copy \"as sdfs \" \"as\"\n"
				+ "	/* Block comment */\n"
				+ "	copy node:\"as\" root:\"as\"\n"
				+ "	onerror fail\n"
				+ "	exec \"python\" \"$x\" \"test\"\n"
				+ "	shexec \"as\"\n"
				+ "	lexec \"/usr/bin/python\" \"/usr/bin/python\" \"script.py\"\n"
				+ "	lpexec \"python\" \"python\" \"script.py\"\n"
				+ "endtask", new ArrayList<>()).build();
	}

	public static CompiledRun getSimpleSampleExperiment() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException, PlanfileParseException {
		return PARSE_API.parseRunToBuilder(
				"variable x index 0 list \"value-x-0\" \"value-x-1\"\n"
				+ "variable y index 1 list \"value-y-0\" \"value-y-1\"\n"
				+ "\n"
				+ "jobs\n"
				+ "	0001 0 0\n"
				+ "endjobs\n"
				+ "task main\n"
				+ "	shexec \"echo $x $y\"\n"
				+ "endtask", new ArrayList<>()).build();
	}

	public static CompiledRun getSimpleSampleEmptyExperiment() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException, PlanfileParseException {
		return PARSE_API.parseRunToBuilder(
				"parameter x\n"
				+ "parameter y\n"
				+ "\n"
				+ "task main\n"
				+ "	shexec \"echo $x $y\"\n"
				+ "endtask", new ArrayList<>()).build();
	}

	public static CompiledRun getBenchRun() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException, PlanfileParseException {
		return PARSE_API.parseRunToBuilder(
				"parameter file text select anyof \"1kb\" \"5kb\" \"10kb\" \"100kb\" \"500kb\" \"1mb\" \"100mb\" \"500mb\" \"1000mb\"\n"
				+ "parameter op text select anyof \"GET\" \"POST\"\n"
				+ "parameter x integer range from 0 to 100 step 1\n"
				+ "\n"
				+ "task main\n"
				+ "\tonerror fail\n"
				+ "\tshexec \"/home/uqzvanim/nimbench.sh $op $file\"\n"
				+ "endtask", new ArrayList<>()).build();
	}

	public static void createSampleResources(NimrodAPI api) {
		api.addResource("root", "dummy", JsonValue.EMPTY_JSON_OBJECT, null, null);
		api.addResource("tinaroo", "dummy", JsonValue.EMPTY_JSON_OBJECT, null, null);
		api.addResource("flashlite", "dummy", JsonValue.EMPTY_JSON_OBJECT, null, null);
		api.addResource("awoonga", "dummy", JsonValue.EMPTY_JSON_OBJECT, null, null);
		api.addResource("nectar", "dummy", JsonValue.EMPTY_JSON_OBJECT, null, null);
	}

	public static final String RSA_PEM_KEY_PUBLIC = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAAgQDMIAQc5QFZfdjImP2T9FNGe9r6l89binb5uH/vxzlnhAtHxesD8B7WXFBN/GxOplb3ih/vadT9gWliXUayvMn+ZMO7iBScnZwdmcMeKP3K80Czlrio+eI3jU77RQPYXBtcD8CBRT94r7nd29I+lMWxOD1U+LBA43kxAbyXqkQ0PQ== nimrod@nimrod";
	public static final String RSA_PEM_KEY_PRIVATE = "-----BEGIN RSA PRIVATE KEY-----\n"
			+ "MIICXQIBAAKBgQDMIAQc5QFZfdjImP2T9FNGe9r6l89binb5uH/vxzlnhAtHxesD\n"
			+ "8B7WXFBN/GxOplb3ih/vadT9gWliXUayvMn+ZMO7iBScnZwdmcMeKP3K80Czlrio\n"
			+ "+eI3jU77RQPYXBtcD8CBRT94r7nd29I+lMWxOD1U+LBA43kxAbyXqkQ0PQIDAQAB\n"
			+ "AoGBAIS7INGFG86MZYVy7hjiG7BOY0LlqiEVNW4GSbKp8ircktU13i7uWa770gAT\n"
			+ "7n1p7k0CVOfCAoxhNRyQGKOq3RDTlM5DjnUdWUZIHolH2mkBNiF4pZARjqXy9LH6\n"
			+ "mBD3SFEYEJ4JPK64Gq4V0O1y5TD926Is84Lhlm+L0oHCAl1lAkEA9EnxJ1zcyQE9\n"
			+ "bj3yR/XUQfmC1EO8xXvfA3UtQROqfMG095lSR7G3Eny8YmrKE7jYJ38hrSY0fMe/\n"
			+ "L4zLLbuZrwJBANXpKQtVAPTeIqTl9Uz0Im1/SER0NaMLINwDsaEYH8uaQ8MDgXig\n"
			+ "X4RGsv9asCOuaIG990RSBQSCIvMyRTGLR9MCQQCTB/Iag/zrClEECkrJ3v77GocQ\n"
			+ "5Rg4MH8g4KT1NzX00s3t/J0WQ7NxcBwejDHGPwnyc8U8JvOOatb6cp5Tj0dHAkAv\n"
			+ "/Xo+15g6V2ewVQL+e7sJk8ezy9qItKNvmMiOGqpvdDGFm9C9LkWfmHjp/v+LUcKS\n"
			+ "cPr7cec8RrHum7WYUuYPAkB4t33FTP31lI7VeRpR+5W77BP78dSvv11RH+kKME1n\n"
			+ "pFqF3KeBYNpq66XTLSjNTM8lcJgNfzrMEED/DyXYuMlx\n"
			+ "-----END RSA PRIVATE KEY-----";
}
