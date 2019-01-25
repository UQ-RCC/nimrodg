package au.edu.uq.rcc.nimrodg.parsing.visitors;

import java.util.List;
import static java.util.stream.Collectors.toList;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParser;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParserBaseVisitor;

public class ArgListVisitor extends NimrodFileParserBaseVisitor<List<String>>{

	public static ArgListVisitor INSTANCE = new ArgListVisitor();

	@Override
	public List<String> visitArgList(NimrodFileParser.ArgListContext ctx) {
		return ctx.sliteral()
				.stream()
				.map(cmd -> cmd.accept(LiteralVisitor.INSTANCE))
				.collect(toList());
	}
}
