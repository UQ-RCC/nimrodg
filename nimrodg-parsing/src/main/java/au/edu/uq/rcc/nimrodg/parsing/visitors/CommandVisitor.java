package au.edu.uq.rcc.nimrodg.parsing.visitors;

import au.edu.uq.rcc.nimrodg.api.CopyCommand;
import au.edu.uq.rcc.nimrodg.api.OnErrorCommand;
import au.edu.uq.rcc.nimrodg.api.RedirectCommand;
import au.edu.uq.rcc.nimrodg.api.utils.SubstitutionException;
import au.edu.uq.rcc.nimrodg.api.utils.run.CompiledCommand;
import au.edu.uq.rcc.nimrodg.api.utils.run.CopyCommandBuilder;
import au.edu.uq.rcc.nimrodg.api.utils.run.ExecCommandBuilder;
import au.edu.uq.rcc.nimrodg.api.utils.run.OnErrorCommandBuilder;
import au.edu.uq.rcc.nimrodg.api.utils.run.RedirectCommandBuilder;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParser;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParserBaseVisitor;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.TerminalNode;

public class CommandVisitor extends NimrodFileParserBaseVisitor<CompiledCommand> {

	public static final CommandVisitor INSTANCE = new CommandVisitor();

	private static OnErrorCommand.Action parseAction(TerminalNode action) {

		switch(action.getText().toLowerCase()) {
			case "ignore":
				return OnErrorCommand.Action.Ignore;
			case "fail":
				return OnErrorCommand.Action.Fail;
		}

		throw new IllegalArgumentException();
	}

	private static CopyCommand.Context parseContext(TerminalNode context) {
		if(context == null) {
			return CopyCommand.Context.Node;
		}

		switch(context.getText()) {
			case "root":
				return CopyCommand.Context.Root;
			case "node":
				return CopyCommand.Context.Node;
		}

		throw new IllegalArgumentException();
	}

	private static RedirectCommand.Stream parseStream(NimrodFileParser.RedirectStreamContext ctx) {
		switch(ctx.getText()) {
			case "stdout":
				return RedirectCommand.Stream.Stdout;
			case "stderr":
				return RedirectCommand.Stream.Stderr;
		}

		throw new IllegalArgumentException();
	}

	@Override
	public CompiledCommand visitShexecCommand(NimrodFileParser.ShexecCommandContext ctx) {
		try {
			return new ExecCommandBuilder()
					.program("")
					.addArgument(ctx.sliteral().accept(LiteralVisitor.INSTANCE))
					.searchPath(false)
					.build();
		} catch(SubstitutionException e) {
			throw new ParseCancellationException(e);
		}
	}

	@Override
	public CompiledCommand visitLpexecCommand(NimrodFileParser.LpexecCommandContext ctx) {
		try {
			return new ExecCommandBuilder()
					.program(ctx.literal().accept(LiteralVisitor.INSTANCE))
					.addArguments(ctx.argList().accept(ArgListVisitor.INSTANCE))
					.searchPath(true)
					.build();
		} catch(SubstitutionException e) {
			throw new ParseCancellationException(e);
		}
	}

	@Override
	public CompiledCommand visitLexecCommand(NimrodFileParser.LexecCommandContext ctx) {
		try {
			return new ExecCommandBuilder()
					.program(ctx.literal().accept(LiteralVisitor.INSTANCE))
					.addArguments(ctx.argList().accept(ArgListVisitor.INSTANCE))
					.searchPath(false)
					.build();
		} catch(SubstitutionException e) {
			throw new ParseCancellationException(e);
		}
	}

	@Override
	public CompiledCommand visitExecCommand(NimrodFileParser.ExecCommandContext ctx) {
		String prog = ctx.literal().accept(LiteralVisitor.INSTANCE);
		try {
			return new ExecCommandBuilder()
					.program(prog)
					.addArgument(prog)
					.addArguments(ctx.argList().accept(ArgListVisitor.INSTANCE))
					.searchPath(true)
					.build();
		} catch(SubstitutionException e) {
			throw new ParseCancellationException(e);
		}
	}

	@Override
	public CompiledCommand visitCopyCommand(NimrodFileParser.CopyCommandContext ctx) {
		try {
			return new CopyCommandBuilder()
					.sourceContext(parseContext(ctx.copyFile(0).TM_CONTEXT()))
					.sourcePath(ctx.copyFile(0).accept(LiteralVisitor.INSTANCE))
					.destContext(parseContext(ctx.copyFile(1).TM_CONTEXT()))
					.destPath(ctx.copyFile(1).accept(LiteralVisitor.INSTANCE))
					.build();
		} catch(SubstitutionException e) {
			throw new ParseCancellationException(e);
		}
	}

	@Override
	public CompiledCommand visitOnerrorCommand(NimrodFileParser.OnerrorCommandContext ctx) {
		return new OnErrorCommandBuilder().action(parseAction(ctx.TM_ACTION())).build();
	}

	@Override
	public CompiledCommand visitRedirectCommand(NimrodFileParser.RedirectCommandContext ctx) {
		try {
			RedirectCommandBuilder cb = new RedirectCommandBuilder()
					.stream(parseStream(ctx.redirectStream()));

			NimrodFileParser.RedirectTargetContext tctx = ctx.redirectTarget();

			if(tctx.TM_TO() != null) {
				/* TO <file */
				cb.append(tctx.TM_APPEND() != null);
				cb.file(tctx.sliteral().accept(LiteralVisitor.INSTANCE));
			} else if(tctx.TM_OFF() != null) {
				/* OFF */
				cb.append(false);
				cb.file("");
			} else {
				throw new IllegalArgumentException();
			}
			return cb.build();
		} catch(SubstitutionException e) {
			throw new ParseCancellationException(e);
		}
	}

	@Override
	public CompiledCommand visitSlurpCommand(NimrodFileParser.SlurpCommandContext ctx) {
		return super.visitSlurpCommand(ctx);
	}
}
