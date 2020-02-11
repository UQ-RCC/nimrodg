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

import au.edu.uq.rcc.nimrodg.api.Command;
import au.edu.uq.rcc.nimrodg.api.CopyCommand;
import au.edu.uq.rcc.nimrodg.api.Task;
import au.edu.uq.rcc.nimrodg.api.utils.run.JsonUtils;
import au.edu.uq.rcc.nimrodg.api.utils.CompiledSubstitution;
import au.edu.uq.rcc.nimrodg.api.utils.SubstitutionException;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledArgument;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledCommand;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledCopyCommand;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledExecCommand;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledJob;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledRun;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledTask;
import au.edu.uq.rcc.nimrodg.api.utils.run.RunBuilder;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileLexer;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParser;
import au.edu.uq.rcc.nimrodg.parsing.visitors.NimrodFileVisitor;
import java.io.IOException;
import java.util.List;
import java.util.function.BiFunction;
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
import org.junit.Assert;
import org.junit.Test;

public class ParseTests {

	private static CharStream getFile(String f) throws IOException {
		return CharStreams.fromStream(ParseTests.class.getResourceAsStream(f));
	}

	private static CharStream getString(String s) {
		return CharStreams.fromString(s);
	}

	private static RunBuilder getRunBuilder(CharStream s) throws IOException {
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

	@Test(expected = RunBuilder.DuplicateVariableException.class)
	public void runfileVariableDuplicateNameTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException {
		getRunBuilder(getFile("onlyvars-dup-name.run")).build();
	}

	@Test(expected = RunBuilder.DuplicateVariableIndexException.class)
	public void runfileVariableDuplicateIndexTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException {
		getRunBuilder(getFile("onlyvars-dup-index.run")).build();
	}

	@Test(expected = RunBuilder.NonConsecutiveVariableIndexException.class)
	public void runfileVariableNonConsecutiveIndexTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException {
		getRunBuilder(getFile("onlyvars-bad-index.run")).build();
	}

	@Test(expected = RunBuilder.FirstVariableIndexNonzero.class)
	public void runfileVariableFistIndexNonzero() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException {
		getRunBuilder(getFile("onlyvars-first-nonzero.run")).build();
	}

	@Test
	public void runfileVariableGoodTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException {
		getRunBuilder(getFile("onlyvars-good.run")).build();
	}

	@Test(expected = RunBuilder.FirstJobIndexNononeException.class)
	public void runfileJobFirstIndexNononeTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException {
		getRunBuilder(getFile("onlyjobs-first-nonone.run")).build();
	}

	@Test(expected = RunBuilder.NonConsecutiveJobIndexException.class)
	public void runfileJobNonConsecutiveIndexTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException {
		getRunBuilder(getFile("onlyjobs-nonconsec-index.run")).build();
	}

	@Test(expected = RunBuilder.InvalidJobVariablesException.class)
	public void runfileJobTooManyVariablesTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException {
		getRunBuilder(getFile("onlyjobs-vars-toomany.run")).build();
	}

	@Test(expected = RunBuilder.InvalidJobVariablesException.class)
	public void runfileJobTooFewVariablesTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException {
		getRunBuilder(getFile("onlyjobs-vars-toofew.run")).build();
	}

	@Test(expected = RunBuilder.InvalidJobVariableIndexException.class)
	public void runfileJobInvalidVariableValueTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException {
		getRunBuilder(getFile("onlyjobs-vars-badval.run")).build();
	}

	@Test
	public void runfileJobGoodTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException {
		getRunBuilder(getFile("onlyjobs-good.run")).build();
	}

	@Test(expected = RunBuilder.DuplicateTaskNameException.class)
	public void runfileTaskDuplicateNameTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException {
		getRunBuilder(getFile("onlytasks-dup-name.run")).build();
	}

	@Test
	public void runfileTaskPathTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException {
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
			Assert.assertEquals(cmd.type, Command.Type.Exec);

			/* search_path should be false, and program should be null. */
			CompiledExecCommand ccmd = (CompiledExecCommand)cmd;
			Assert.assertFalse(ccmd.searchPath);
			Assert.assertTrue(ccmd.program.isEmpty());

			List<CompiledArgument> args = ccmd.arguments;
			Assert.assertEquals(1, args.size());

			Assert.assertEquals(0, args.get(0).getSubstitutions().size());

			Assert.assertEquals(arg1, args.get(0).getText());
			return null;
		};

		CompiledRun r = getRunBuilder(getString(s)).build();

		Assert.assertEquals(1, r.numTasks);

		CompiledTask task = r.tasks.get(0);
		Assert.assertEquals(Task.Name.Main, task.name);

		List<CompiledCommand> commands = task.commands;
		Assert.assertEquals(4, commands.size());

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
	public void substitutionFormatsTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException {
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

		Assert.assertEquals(1, r.numTasks);

		CompiledTask task = r.tasks.get(0);
		Assert.assertEquals(Task.Name.Main, task.name);

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

		Assert.assertEquals(cargs.length, commands.size());

		for(int i = 0; i < commands.size(); ++i) {
			CompiledCommand cmd = commands.get(i);
			Assert.assertEquals(cmd.type, Command.Type.Exec);

			CompiledExecCommand ccmd = (CompiledExecCommand)cmd;
			Assert.assertFalse(ccmd.searchPath);
			Assert.assertTrue(ccmd.program.isEmpty());

			List<CompiledArgument> args = ccmd.arguments;
			Assert.assertEquals(1, args.size());

			CompiledArgument arg = args.get(0);
			Assert.assertEquals(cargs[i].text, arg.getText());
			Assert.assertEquals(1, arg.getSubstitutions().size());
			Assert.assertEquals(cargs[i].sub, arg.getSubstitutions().get(0));
		}
	}

	@Test
	public void runfileTaskGoodTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException {
		CompiledRun r = getRunBuilder(getFile("onlytasks-good.run")).build();

		Assert.assertEquals(1, r.numTasks);

		CompiledTask _task = r.tasks.get(0);
		Assert.assertEquals(_task.name, Task.Name.Main);
	}

	private static void assertCopyCommand(CompiledCommand cmd, CopyCommand.Context srcCtx, String srcPath, CopyCommand.Context dstCtx, String dstPath) {
		Assert.assertEquals(Command.Type.Copy, cmd.type);

		CompiledCopyCommand ccmd = (CompiledCopyCommand)cmd;

		Assert.assertEquals(srcCtx, ccmd.sourceContext);
		Assert.assertEquals(srcPath, ccmd.sourcePath.getText());
		Assert.assertEquals(dstCtx, ccmd.destContext);
		Assert.assertEquals(dstPath, ccmd.destPath.getText());
	}

	@Test
	public void runfileCopyContextTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException {
		String s = "variable x index 0 list \"0\"\n"
				+ "jobs\n"
				+ "endjobs\n"
				+ "task main\n"
				+ "	copy root:relfile-in.txt node:relfile-out.txt\n"
				+ "	copy root:/bin.sh node:/bin/sh\n"
				+ "	copy root:../../oops node:.\n"
				+ "endtask";

		CompiledRun r = getRunBuilder(getString(s)).build();

		Assert.assertEquals(1, r.numTasks);

		CompiledTask t = r.tasks.get(0);
		Assert.assertEquals(Task.Name.Main, t.name);

		List<CompiledCommand> commands = t.commands;
		Assert.assertEquals(3, commands.size());

		assertCopyCommand(commands.get(0), CopyCommand.Context.Root, "relfile-in.txt", CopyCommand.Context.Node, "relfile-out.txt");
		assertCopyCommand(commands.get(1), CopyCommand.Context.Root, "/bin.sh", CopyCommand.Context.Node, "/bin/sh");
		assertCopyCommand(commands.get(2), CopyCommand.Context.Root, "../../oops", CopyCommand.Context.Node, ".");

	}

	@Test
	public void nonQuotedArgumentSeparationTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException {
		String s = "variable x index 0 list \"value-x-0\"\n"
				+ "jobs\n"
				+ "endjobs\n"
				+ "task main\n"
				+ "	lpexec python \"\" /path/to/script.py\n"
				+ "endtask";
		//dumpTokens(new CommonTokenStream(new NimrodRunfileLexer(getString(s))), NimrodRunfileLexer.VOCABULARY);

		RunBuilder rb = getRunBuilder(getString(s));
		CompiledRun r = rb.build();

		Assert.assertEquals(1, r.numTasks);

		CompiledTask t = r.tasks.get(0);
		Assert.assertEquals(Task.Name.Main, t.name);

		List<CompiledCommand> commands = t.commands;
		Assert.assertEquals(1, commands.size());

		CompiledExecCommand cmd = (CompiledExecCommand)commands.get(0);

		Assert.assertEquals("python", cmd.program);

		String[] expectedArgs = new String[]{
			"",
			"/path/to/script.py"
		};

		Assert.assertArrayEquals(expectedArgs, cmd.arguments.stream().map(a -> a.getText()).toArray());
	}

	@Test(expected = RunBuilder.InvalidVariableSubstitutionReferenceException.class)
	public void invalidVariableSubstitutionTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException {
		getRunBuilder(getFile("invalid-variable-substitution.pln")).build();
	}

	@Test
	public void aeroSampleTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException {
		CompiledRun rr = getRunBuilder(getFile("aero-onlyvars.pln")).build();

		String s = JsonUtils.toJson(rr).toString();
		int x = 0;
	}

	@Test
	public void webtest() throws IOException, RunBuilder.RunfileBuildException {
		CompiledRun rr = getRunBuilder(getString(
				"parameter file text select anyof \"1kb\" \"5kb\" \"10kb\" \"100kb\" \"500kb\" \"1mb\" \"100mb\" \"500mb\" \"1000mb\"\n"
				+ "parameter op text select anyof \"GET\" \"POST\"\n"
				+ "parameter x integer range from 0 to 100 step 1\n"
				+ "\n"
				+ "task main\n"
				+ "\tonerror fail\n"
				+ "\tshexec \"/home/uqzvanim/nimbench.sh $op $file\"\n"
				+ "endtask")).build();

		Assert.assertEquals(1818, rr.numJobs);
		Assert.assertEquals(3, rr.numVariables);
		Assert.assertEquals(1, rr.numTasks);

	}

	@Test
	public void singleJobTest() throws IOException, RunBuilder.RunfileBuildException {
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

		Assert.assertEquals(1, rr.numJobs);

		CompiledJob j = rr.jobs.get(0);
		Assert.assertEquals(1, j.index);

		int[] indices = j.indices;
		Assert.assertEquals(2, indices.length);

		Assert.assertEquals(0, indices[0]);
		Assert.assertEquals(0, indices[1]);
	}

	@Test
	public void noJobTest() throws IOException, RunBuilder.RunfileBuildException {
		CompiledRun rr = getRunBuilder(getString(
				"variable x index 0 list \"0\" \"1\"\n"
				+ "variable y index 1 list \"0\" \"1\"\n"
				+ "\n"
				+ "jobs\n"
				+ "endjobs\n"
				+ "task main\n"
				+ "    onerror ignore\n"
				+ "endtask")).build();

		Assert.assertEquals(0, rr.jobs.size());
	}

	@Test(expected = ParseCancellationException.class)
	public void parameterMissingTypeTest() throws IOException, RunBuilder.RunfileBuildException {
		CompiledRun rr = getRunBuilder(getString(
				"parameter x range from 0 to 100 step 1\n"
				+ "task main\n"
				+ "    onerror fail\n"
				+ "    shexec \"uname -a > $jobindex.txt\"\n"
				+ "    shexec \"echo variable x = $x >> $jobindex.txt\"\n"
				+ "    copy node:$jobindex.txt root:$jobindex.txt\n"
				+ "endtask")).build();
	}

	@Test
	public void commentsAboveParametersTest() throws IOException, RunBuilder.RunfileBuildException {
		CompiledRun rr = getRunBuilder(getString("// I AM A COMMENT\n"
				+ "parameter x integer range from 0 to 100 step 1\n"
				+ "task main\n"
				+ "    onerror fail\n"
				+ "endtask")).build();
	}

	@Test
	public void redirectSyntaxTest() throws IOException, RunBuilder.RunfileBuildException {
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
	public void emptyParameterTest() throws IOException, RunBuilder.RunfileBuildException {
		String pln
				= "parameter x\n"
				+ "task main\n"
				+ "    onerror fail\n"
				+ "endtask";
		CompiledRun rr = getRunBuilder(getString(pln)).build();
	}

	@Test
	public void execWithCopyAsArgumentTest() throws IOException, RunBuilder.RunfileBuildException {
		String pln
				= "parameter x\n"
				+ "task main\n"
				+ "    exec echo copy back files\n"
				+ "    exec echo copy\n"
				+ "    copy node:a root:a\n"
				+ "endtask";
		CompiledRun rr = getRunBuilder(getString(pln)).build();
	}

	@Test(expected = ParseCancellationException.class)
	public void badTaskNameTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException {
		getRunBuilder(getFile("test_badtask.pln")).build();
	}

	@Test(expected = ParseCancellationException.class)
	public void truncParamTest() throws IOException, RunBuilder.RunfileBuildException, SubstitutionException {
		getRunBuilder(getFile("test_truncparam.pln")).build();
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
