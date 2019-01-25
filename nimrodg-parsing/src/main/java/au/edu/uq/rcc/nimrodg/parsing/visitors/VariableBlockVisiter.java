package au.edu.uq.rcc.nimrodg.parsing.visitors;

import au.edu.uq.rcc.nimrodg.api.utils.run.VariableBuilder;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParser;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParserBaseVisitor;
import java.util.ArrayList;
import java.util.List;

public class VariableBlockVisiter extends NimrodFileParserBaseVisitor<List<VariableBuilder>> {

	@Override
	public List<VariableBuilder> visitVariableBlock(NimrodFileParser.VariableBlockContext ctx) {

		List<VariableBuilder> vars = new ArrayList<>();
		ctx.accept(new NimrodFileParserBaseVisitor<Void>() {
			@Override
			public Void visitVariableStatement(NimrodFileParser.VariableStatementContext ctx) {
				vars.add(ctx.accept(VariableVisitor.INSTANCE));
				return null;
			}

			@Override
			public Void visitParameterStatement(NimrodFileParser.ParameterStatementContext ctx) {
				vars.add(ctx.accept(VariableVisitor.INSTANCE));
				return null;
			}
		});

		return vars;
	}

	public static final VariableBlockVisiter INSTANCE = new VariableBlockVisiter();

}
