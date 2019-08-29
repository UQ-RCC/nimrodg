package au.edu.uq.rcc.nimrodg.parsing.visitors;

import au.edu.uq.rcc.nimrodg.api.utils.EscapeException;
import au.edu.uq.rcc.nimrodg.api.utils.StringUtils;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParser;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParserBaseVisitor;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * LITERALLY
 */
public class LiteralVisitor extends NimrodFileParserBaseVisitor<String> {

	public static final LiteralVisitor INSTANCE = new LiteralVisitor();

	@Override
	public String visitLiteral(NimrodFileParser.LiteralContext ctx) {
		TerminalNode sl = ctx.STRING_LITERAL();
		if(sl == null) {
			return ctx.getText();
		}

		return unescape(sl.getText());
	}

	@Override
	public String visitSliteral(NimrodFileParser.SliteralContext ctx) {
		TerminalNode sl = ctx.STRING_LITERAL();
		if(sl == null) {
			return ctx.getText();
		}

		return unescape(sl.getText());
	}

	@Override
	public String visitVariableValue(NimrodFileParser.VariableValueContext ctx) {
		TerminalNode sl = ctx.STRING_LITERAL();
		if(sl != null) {
			return LiteralVisitor.unescape(sl.getText());
		} else {
			return ctx.number().getText();
		}
	}


//	@Override
//	public String visitSubstitution(NimrodFileParser.SubstitutionContext ctx) {
//		return ctx.getText();
//	}
	public static String unescape(String s) throws ParseCancellationException {
		try {
			return StringUtils.unescape(s.substring(1, s.length() - 1));
		} catch(EscapeException e) {
			/* ANTLR should catch this before we do. If you get this, check the grammar or CString#unescape(). */
			throw new ParseCancellationException("Invalid escape sequence in string. This should never happen.", e);
		}
	}
}
