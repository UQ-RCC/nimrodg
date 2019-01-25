package au.edu.uq.rcc.nimrodg.parsing.visitors;

import au.edu.uq.rcc.nimrodg.api.utils.run.RunBuilder;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParser;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParserBaseVisitor;

public class NimrodFileVisitor extends NimrodFileParserBaseVisitor<RunBuilder> {

	public static NimrodFileVisitor INSTANCE = new NimrodFileVisitor();

	@Override
	public RunBuilder visitNimrodFile(NimrodFileParser.NimrodFileContext ctx) {
		RunBuilder rb = new RunBuilder();

		rb.addVariables(ctx.variableBlock().accept(VariableBlockVisiter.INSTANCE));

		ctx.accept(new NimrodFileParserBaseVisitor<RunBuilder>() {
			@Override
			public RunBuilder visitJobEntry(NimrodFileParser.JobEntryContext ctx) {
				return rb.addJob(ctx.accept(JobVisitor.INSTANCE));
			}

		});

		ctx.accept(new NimrodFileParserBaseVisitor<RunBuilder>() {
			@Override
			public RunBuilder visitTaskBlock(NimrodFileParser.TaskBlockContext ctx) {
				return rb.addTask(ctx.accept(TaskVisitor.INSTANCE));
			}

		});

		return rb;
	}

}
