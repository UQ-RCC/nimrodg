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
package au.edu.uq.rcc.nimrodg.parsing;

import au.edu.uq.rcc.nimrodg.api.Command;
import au.edu.uq.rcc.nimrodg.api.CopyCommand;
import au.edu.uq.rcc.nimrodg.api.Task;
import au.edu.uq.rcc.nimrodg.api.utils.CompiledSubstitution;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledArgument;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledCommand;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledCopyCommand;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledExecCommand;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledJob;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledTask;
import au.edu.uq.rcc.nimrodg.api.utils.run.JsonUtils;
import au.edu.uq.rcc.nimrodg.api.utils.run.RunBuilder;
import au.edu.uq.rcc.nimrodg.api.utils.run.RunfileBuildException;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileLexer;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParser;
import au.edu.uq.rcc.nimrodg.parsing.visitors.NimrodFileVisitor;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.DiagnosticErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.function.BiFunction;

public class ParseTests {

	private static CharStream getFile(String f) throws IOException {
		return CharStreams.fromStream(ParseTests.class.getResourceAsStream(f));
	}

	private static CharStream getString(String s) {
		return CharStreams.fromString(s);
	}

	private static RunBuilder getRunBuilder(CharStream s) {
		NimrodFileParser parser = new NimrodFileParser(new CommonTokenStream(new NimrodFileLexer(s)));
		parser.removeErrorListeners();
		parser.setErrorHandler(new BailErrorStrategy() {
			@Override
			public void reportError(Parser recognizer, RecognitionException e) {
				/* Kill the unit test immediately */
				e.printStackTrace(System.err);
				throw new ParseCancellationException(e);
			}

			@Override
			public Token recoverInline(Parser recognizer) throws RecognitionException {
				try {
					return super.recoverInline(recognizer);
				} catch(ParseCancellationException e) {
					e.printStackTrace(System.err);
					throw e;
				}
			}

			@Override
			public void recover(Parser recognizer, RecognitionException e) {
				try {
					super.recover(recognizer, e);
				} catch(ParseCancellationException ex) {
					ex.printStackTrace(System.err);
					e.printStackTrace(System.err);
					throw ex;
				}
			}

		});
		parser.addErrorListener(new DiagnosticErrorListener(false));
		parser.addErrorListener(ConsoleErrorListener.INSTANCE);

		return parser.nimrodFile().accept(NimrodFileVisitor.INSTANCE);
	}

	@Test
	public void runfileVariableDuplicateNameTest() {
		Assertions.assertThrows(RunfileBuildException.DuplicateVariable.class, () -> getRunBuilder(getFile("onlyvars-dup-name.run")).build());
	}

	@Test
	public void runfileVariableDuplicateIndexTest() {
		Assertions.assertThrows(RunfileBuildException.DuplicateVariableIndex.class, () -> getRunBuilder(getFile("onlyvars-dup-index.run")).build());
	}

	@Test
	public void runfileVariableNonConsecutiveIndexTest() {
		Assertions.assertThrows(RunfileBuildException.NonConsecutiveVariableIndex.class, () -> getRunBuilder(getFile("onlyvars-bad-index.run")).build());
	}

	@Test
	public void runfileVariableFistIndexNonzero() {
		Assertions.assertThrows(RunfileBuildException.FirstVariableIndexNonZero.class, () -> getRunBuilder(getFile("onlyvars-first-nonzero.run")).build());
	}

	@Test
	public void runfileVariableGoodTest() throws IOException, RunfileBuildException {
		getRunBuilder(getFile("onlyvars-good.run")).build();
	}

	@Test
	public void runfileJobFirstIndexNononeTest() {
		Assertions.assertThrows(RunfileBuildException.FirstJobIndexNonOne.class, () -> getRunBuilder(getFile("onlyjobs-first-nonone.run")).build());
	}

	@Test
	public void runfileJobNonConsecutiveIndexTest() {
		Assertions.assertThrows(RunfileBuildException.NonConsecutiveJobIndex.class, () -> getRunBuilder(getFile("onlyjobs-nonconsec-index.run")).build());
	}

	@Test
	public void runfileJobTooManyVariablesTest() {
		Assertions.assertThrows(RunfileBuildException.InvalidJobVariables.class, () -> getRunBuilder(getFile("onlyjobs-vars-toomany.run")).build());
	}

	@Test
	public void runfileJobTooFewVariablesTest() {
		Assertions.assertThrows(RunfileBuildException.InvalidJobVariables.class, () -> getRunBuilder(getFile("onlyjobs-vars-toofew.run")).build());
	}

	@Test
	public void runfileJobInvalidVariableValueTest() {
		Assertions.assertThrows(RunfileBuildException.InvalidJobVariableIndex.class, () -> getRunBuilder(getFile("onlyjobs-vars-badval.run")).build());
	}

	@Test
	public void runfileJobGoodTest() throws IOException, RunfileBuildException {
		getRunBuilder(getFile("onlyjobs-good.run")).build();
	}

	@Test
	public void runfileTaskDuplicateNameTest() {
		Assertions.assertThrows(RunfileBuildException.DuplicateTaskName.class, () -> getRunBuilder(getFile("onlytasks-dup-name.run")).build());
	}

	@Test
	public void runfileTaskPathTest() throws RunfileBuildException {
		String s = "variable x index 0 list \"0\"\n"
				+ "jobs\n"
				+ "endjobs\n"
				+ "task main\n"
				+ "	shexec \"/absolute/path/folder/\"\n"
				+ "	shexec /absolute/path/folder/\n"
				+ "	shexec \"/absolute/path/file\"\n"
				+ "	shexec /absolute/path/file\n"
				+ "endtask\n";

		BiFunction<CompiledCommand, String, Void> checkShexec = (cmd, arg1) -> {
			Assertions.assertEquals(cmd.type, Command.Type.Exec);

			/* search_path should be false, and program should be null. */
			CompiledExecCommand ccmd = (CompiledExecCommand)cmd;
			Assertions.assertFalse(ccmd.searchPath);
			Assertions.assertTrue(ccmd.program.isEmpty());

			List<CompiledArgument> args = ccmd.arguments;
			Assertions.assertEquals(1, args.size());

			Assertions.assertEquals(0, args.get(0).getSubstitutions().size());

			Assertions.assertEquals(arg1, args.get(0).getText());
			return null;
		};

		CompiledRun r = getRunBuilder(getString(s)).build();

		Assertions.assertEquals(1, r.numTasks);

		CompiledTask task = r.tasks.get(0);
		Assertions.assertEquals(Task.Name.Main, task.name);

		List<CompiledCommand> commands = task.commands;
		Assertions.assertEquals(4, commands.size());

		/* Quoted */
		checkShexec.apply(commands.get(0), "/absolute/path/folder/");

		/* Unquoted */
		checkShexec.apply(commands.get(1), "/absolute/path/folder/");

		/* Quoted */
		checkShexec.apply(commands.get(2), "/absolute/path/file");

		/* Unquoted */
		checkShexec.apply(commands.get(3), "/absolute/path/file");
	}

	@Test
	public void substitutionFormatsTest() throws RunfileBuildException {
		String s = "variable x index 0 list \"0\"\n"
				+ "jobs\n"
				+ "endjobs\n"
				+ "task main\n"
				+ "	shexec $x\n"
				+ "	shexec ${x}\n"
				+ "	shexec /path/to/${x}/asdf\n"
				+ "	shexec /path/to/$x/asdf\n"
				+ "	shexec /path/to/asdf${x}/asdf\n"
				+ "	shexec /path/to/asdf$x/asdf\n"
				+ "	shexec /path/to/asdf${x}asdf/asdf\n"
				+ "	shexec \"quoted$x\"\n"
				+ "	shexec \"quoted$x quoted\"\n"
				+ "	shexec \"quoted${x}\"\n"
				+ "	shexec \"quoted${x}quoted\"\n"
				+ "endtask";
		//dumpTokens(new CommonTokenStream(new NimrodRunfileLexer(getString(s))), NimrodRunfileLexer.VOCABULARY);

		CompiledRun r = getRunBuilder(getString(s)).build();

		Assertions.assertEquals(1, r.numTasks);

		CompiledTask task = r.tasks.get(0);
		Assertions.assertEquals(Task.Name.Main, task.name);

		class CArg {

			public CArg(String text, CompiledSubstitution sub) {
				this.text = text;
				this.sub = sub;
			}
			public final String text;
			public final CompiledSubstitution sub;
		}

		CArg[] cargs = new CArg[]{
			new CArg("$x", new CompiledSubstitution("x", 0, 2, 0)),
			new CArg("${x}", new CompiledSubstitution("x", 0, 4, 0)),
			new CArg("/path/to/${x}/asdf", new CompiledSubstitution("x", 9, 13, 9)),
			new CArg("/path/to/$x/asdf", new CompiledSubstitution("x", 9, 11, 9)),
			new CArg("/path/to/asdf${x}/asdf", new CompiledSubstitution("x", 13, 17, 13)),
			new CArg("/path/to/asdf$x/asdf", new CompiledSubstitution("x", 13, 15, 13)),
			new CArg("/path/to/asdf${x}asdf/asdf", new CompiledSubstitution("x", 13, 17, 13)),
			new CArg("quoted$x", new CompiledSubstitution("x", 6, 8, 6)),
			new CArg("quoted$x quoted", new CompiledSubstitution("x", 6, 8, 6)),
			new CArg("quoted${x}", new CompiledSubstitution("x", 6, 10, 6)),
			new CArg("quoted${x}quoted", new CompiledSubstitution("x", 6, 10, 6)),};

		List<CompiledCommand> commands = task.commands;

		Assertions.assertEquals(cargs.length, commands.size());

		for(int i = 0; i < commands.size(); ++i) {
			CompiledCommand cmd = commands.get(i);
			Assertions.assertEquals(cmd.type, Command.Type.Exec);

			CompiledExecCommand ccmd = (CompiledExecCommand)cmd;
			Assertions.assertFalse(ccmd.searchPath);
			Assertions.assertTrue(ccmd.program.isEmpty());

			List<CompiledArgument> args = ccmd.arguments;
			Assertions.assertEquals(1, args.size());

			CompiledArgument arg = args.get(0);
			Assertions.assertEquals(cargs[i].text, arg.getText());
			Assertions.assertEquals(1, arg.getSubstitutions().size());
			Assertions.assertEquals(cargs[i].sub, arg.getSubstitutions().get(0));
		}
	}

	@Test
	public void runfileTaskGoodTest() throws IOException, RunfileBuildException {
		CompiledRun r = getRunBuilder(getFile("onlytasks-good.run")).build();

		Assertions.assertEquals(1, r.numTasks);

		CompiledTask _task = r.tasks.get(0);
		Assertions.assertEquals(_task.name, Task.Name.Main);
	}

	private static void assertCopyCommand(CompiledCommand cmd, CopyCommand.Context srcCtx, String srcPath, CopyCommand.Context dstCtx, String dstPath) {
		Assertions.assertEquals(Command.Type.Copy, cmd.type);

		CompiledCopyCommand ccmd = (CompiledCopyCommand)cmd;

		Assertions.assertEquals(srcCtx, ccmd.sourceContext);
		Assertions.assertEquals(srcPath, ccmd.sourcePath.getText());
		Assertions.assertEquals(dstCtx, ccmd.destContext);
		Assertions.assertEquals(dstPath, ccmd.destPath.getText());
	}

	@Test
	public void runfileCopyContextTest() throws RunfileBuildException {
		String s = "variable x index 0 list \"0\"\n"
				+ "jobs\n"
				+ "endjobs\n"
				+ "task main\n"
				+ "	copy root:relfile-in.txt node:relfile-out.txt\n"
				+ "	copy root:/bin.sh node:/bin/sh\n"
				+ "	copy root:../../oops node:.\n"
				+ "endtask";

		CompiledRun r = getRunBuilder(getString(s)).build();

		Assertions.assertEquals(1, r.numTasks);

		CompiledTask t = r.tasks.get(0);
		Assertions.assertEquals(Task.Name.Main, t.name);

		List<CompiledCommand> commands = t.commands;
		Assertions.assertEquals(3, commands.size());

		assertCopyCommand(commands.get(0), CopyCommand.Context.Root, "relfile-in.txt", CopyCommand.Context.Node, "relfile-out.txt");
		assertCopyCommand(commands.get(1), CopyCommand.Context.Root, "/bin.sh", CopyCommand.Context.Node, "/bin/sh");
		assertCopyCommand(commands.get(2), CopyCommand.Context.Root, "../../oops", CopyCommand.Context.Node, ".");

	}

	@Test
	public void nonQuotedArgumentSeparationTest() throws RunfileBuildException {
		String s = "variable x index 0 list \"value-x-0\"\n"
				+ "jobs\n"
				+ "endjobs\n"
				+ "task main\n"
				+ "	lpexec python \"\" /path/to/script.py\n"
				+ "endtask";
		//dumpTokens(new CommonTokenStream(new NimrodRunfileLexer(getString(s))), NimrodRunfileLexer.VOCABULARY);

		RunBuilder rb = getRunBuilder(getString(s));
		CompiledRun r = rb.build();

		Assertions.assertEquals(1, r.numTasks);

		CompiledTask t = r.tasks.get(0);
		Assertions.assertEquals(Task.Name.Main, t.name);

		List<CompiledCommand> commands = t.commands;
		Assertions.assertEquals(1, commands.size());

		CompiledExecCommand cmd = (CompiledExecCommand)commands.get(0);

		Assertions.assertEquals("python", cmd.program);

		String[] expectedArgs = new String[]{
			"",
			"/path/to/script.py"
		};

		Assertions.assertArrayEquals(expectedArgs, cmd.arguments.stream().map(CompiledArgument::getText).toArray());
	}

	@Test
	public void invalidVariableSubstitutionTest() throws IOException, RunfileBuildException {
		Assertions.assertThrows(RunfileBuildException.InvalidVariableSubstitutionReference.class, () -> getRunBuilder(getFile("invalid-variable-substitution.pln")).build());
	}

	@Test
	public void aeroSampleTest() throws IOException, RunfileBuildException {
		CompiledRun rr = getRunBuilder(getFile("aero-onlyvars.pln")).build();

		String s = JsonUtils.toJson(rr).toString();
	}

	@Test
	public void webtest() throws RunfileBuildException {
		CompiledRun rr = getRunBuilder(getString(
				"parameter file text select anyof \"1kb\" \"5kb\" \"10kb\" \"100kb\" \"500kb\" \"1mb\" \"100mb\" \"500mb\" \"1000mb\"\n"
				+ "parameter op text select anyof \"GET\" \"POST\"\n"
				+ "parameter x integer range from 0 to 100 step 1\n"
				+ "\n"
				+ "task main\n"
				+ "\tonerror fail\n"
				+ "\tshexec \"/home/uqzvanim/nimbench.sh $op $file\"\n"
				+ "endtask")).build();

		Assertions.assertEquals(1818, rr.numJobs);
		Assertions.assertEquals(3, rr.numVariables);
		Assertions.assertEquals(1, rr.numTasks);

	}

	@Test
	public void singleJobTest() throws RunfileBuildException {
		CompiledRun rr = getRunBuilder(getString(
				"variable x index 0 list \"0\" \"1\"\n"
				+ "variable y index 1 list \"0\" \"1\"\n"
				+ "\n"
				+ "jobs\n"
				+ "    0001 0 0\n"
				+ "endjobs\n"
				+ "task main\n"
				+ "    onerror ignore\n"
				+ "endtask")).build();

		Assertions.assertEquals(1, rr.numJobs);

		CompiledJob j = rr.jobs.get(0);
		Assertions.assertEquals(1, j.index);

		int[] indices = j.indices;
		Assertions.assertEquals(2, indices.length);

		Assertions.assertEquals(0, indices[0]);
		Assertions.assertEquals(0, indices[1]);
	}

	@Test
	public void noJobTest() throws RunfileBuildException {
		CompiledRun rr = getRunBuilder(getString(
				"variable x index 0 list \"0\" \"1\"\n"
				+ "variable y index 1 list \"0\" \"1\"\n"
				+ "\n"
				+ "jobs\n"
				+ "endjobs\n"
				+ "task main\n"
				+ "    onerror ignore\n"
				+ "endtask")).build();

		Assertions.assertEquals(0, rr.jobs.size());
	}

	@Test
	public void parameterMissingTypeTest() {
		Assertions.assertThrows(ParseCancellationException.class, () -> getRunBuilder(getString(
				"parameter x range from 0 to 100 step 1\n"
				+ "task main\n"
				+ "    onerror fail\n"
				+ "    shexec \"uname -a > $jobindex.txt\"\n"
				+ "    shexec \"echo variable x = $x >> $jobindex.txt\"\n"
				+ "    copy node:$jobindex.txt root:$jobindex.txt\n"
				+ "endtask")).build());
	}

	@Test
	public void commentsTest() throws RunfileBuildException {
		getRunBuilder(getString(
				"// I AM A COMMENT\n"
				+ "parameter x integer range from 0 to 100 step 1\n"
				+ "task main\n"
				+ "    onerror fail\n"
				+ "endtask")).build();

		getRunBuilder(getString(
				"# I AM ALSO A COMMENT\n"
					+ "parameter x integer range from 0 to 100 step 1\n"
					+ "task main\n"
					+ "    onerror fail\n"
					+ "endtask")).build();

		getRunBuilder(getString(
				"parameter x integer range from 0 to 100 step 1\n"
				+ "# Comment 1\n"
				+ "// Comment 2\n"
				+ "task main\n"
				+ "    onerror fail\n"
				+ "endtask")).build();
	}

	@Test
	public void redirectSyntaxTest() throws RunfileBuildException {
		String pln
				= "parameter x integer range from 0 to 100 step 1\n"
				+ "task main\n"
				+ "    redirect stdout off\n"
				+ "    redirect stderr off\n"
				+ "    redirect stdout to /dev/null\n"
				+ "    redirect stderr to /dev/null\n"
				+ "    redirect stdout append to /dev/null\n"
				+ "    redirect stderr append to /dev/null\n"
				+ "    redirect stdout to stdout.$x\n"
				+ "    redirect stderr to stderr.$x\n"
				+ "    redirect stdout append to stdout.$x\n"
				+ "    redirect stderr append to stderr.$x\n"
				+ "endtask";

		//easyDumpTokens(pln);
		CompiledRun rr = getRunBuilder(getString(pln)).build();
	}

	@Test
	public void emptyParameterTest() throws RunfileBuildException {
		String pln
				= "parameter x\n"
				+ "task main\n"
				+ "    onerror fail\n"
				+ "endtask";
		CompiledRun rr = getRunBuilder(getString(pln)).build();
	}

	@Test
	public void execWithCopyAsArgumentTest() throws RunfileBuildException {
		String pln
				= "parameter x\n"
				+ "task main\n"
				+ "    exec echo copy back files\n"
				+ "    exec echo copy\n"
				+ "    copy node:a root:a\n"
				+ "endtask";
		CompiledRun rr = getRunBuilder(getString(pln)).build();
	}

	@Test
	public void rangeWithoutStepTest() throws RunfileBuildException {
		CompiledRun rr = getRunBuilder(getString(
				"parameter x integer range from 0 to 100\n"
				+ "parameter y float range from 0 to 100\n"
				+ "task main\n"
				+ "    onerror fail\n"
				+ "endtask")).build();
	}

	@Test
	public void badTaskNameTest() {
		Assertions.assertThrows(ParseCancellationException.class, () -> getRunBuilder(getFile("test_badtask.pln")).build());
	}

	@Test
	public void truncParamTest() {
		Assertions.assertThrows(ParseCancellationException.class, () -> getRunBuilder(getFile("test_truncparam.pln")).build());
	}

	/*
	CharStream cs = getString(pln);
	CommonTokenStream cts = new CommonTokenStream(new NimrodFileLexer(cs));
	dumpTokens(cts, NimrodFileParser.VOCABULARY);
	 */
	private static void dumpTokens(CommonTokenStream ts, Vocabulary v) {
		ts.fill();

		for(Token t : ts.getTokens()) {
			System.out.printf("'%-32s': %s\n", t.getText().replace("\n", ""), v.getDisplayName(t.getType()));
		}
	}

	private static void easyDumpTokens(String pln) {
		CharStream cs = getString(pln);
		CommonTokenStream cts = new CommonTokenStream(new NimrodFileLexer(cs));
		dumpTokens(cts, NimrodFileParser.VOCABULARY);
	}
}
