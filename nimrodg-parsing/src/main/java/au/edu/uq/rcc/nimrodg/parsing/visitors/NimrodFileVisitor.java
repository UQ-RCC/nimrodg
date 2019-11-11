package au.edu.uq.rcc.nimrodg.parsing.visitors;

import au.edu.uq.rcc.nimrodg.api.utils.run.RunBuilder;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParser;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParserBaseVisitor;

import java.util.Optional;

public class NimrodFileVisitor extends NimrodFileParserBaseVisitor<RunBuilder> {

	public static NimrodFileVisitor INSTANCE = new NimrodFileVisitor();

	@Override
	public RunBuilder visitNimrodFile(NimrodFileParser.NimrodFileContext ctx) {
		RunBuilder rb = new RunBuilder();

		NimrodFileParser.VariableBlockContext vctx = ctx.variableBlock();
		if(vctx != null) {
			rb.addVariables(vctx.accept(VariableBlockVisiter.INSTANCE));
		}

		NimrodFileParser.ResultBlockContext rctx = ctx.resultBlock();
		if(rctx != null) {
			rb.addResults(rctx.resultStatement().stream().map(r -> r.resultName().getText()));
		}

		NimrodFileParser.JobsBlockContext jctx = ctx.jobsBlock();
		if(jctx != null) {
			rb.addJobs(jctx.jobEntry().stream().map(JobVisitor.INSTANCE::visitJobEntry));
		}

		rb.addTasks(ctx.taskBlock().stream().map(TaskVisitor.INSTANCE::visitTaskBlock));
		return rb;
	}

}
