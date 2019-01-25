package au.edu.uq.rcc.nimrodg.parsing.visitors;

import au.edu.uq.rcc.nimrodg.api.Task;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledTask;
import au.edu.uq.rcc.nimrodg.api.utils.run.TaskBuilder;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParser;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParserBaseVisitor;
import org.antlr.v4.runtime.tree.TerminalNode;

public class TaskVisitor extends NimrodFileParserBaseVisitor<CompiledTask> {

	public static final TaskVisitor INSTANCE = new TaskVisitor();

	private static Task.Name parseName(TerminalNode name) {
		switch(name.getText()) {
			case "nodestart":
				return Task.Name.NodeStart;
			case "main":
				return Task.Name.Main;
		}

		/* Will never happen */
		throw new IllegalArgumentException();
	}

	@Override
	public CompiledTask visitTaskBlock(NimrodFileParser.TaskBlockContext ctx) {
		TaskBuilder b = new TaskBuilder();
		b.name(parseName(ctx.TM_TASKNAME()));

		ctx.accept(new NimrodFileParserBaseVisitor<Void>() {
			@Override
			public Void visitTaskCommand(NimrodFileParser.TaskCommandContext ctx) {
				b.addCommand(ctx.accept(CommandVisitor.INSTANCE));
				return null;
			}

		});

		return b.build();
	}

}
