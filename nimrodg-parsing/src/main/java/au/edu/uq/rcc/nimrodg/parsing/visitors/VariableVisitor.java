package au.edu.uq.rcc.nimrodg.parsing.visitors;

import au.edu.uq.rcc.nimrodg.api.utils.EscapeException;
import au.edu.uq.rcc.nimrodg.api.utils.StringUtils;
import au.edu.uq.rcc.nimrodg.api.utils.run.VariableBuilder;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParser;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParserBaseVisitor;
import org.antlr.v4.runtime.misc.ParseCancellationException;

public class VariableVisitor extends NimrodFileParserBaseVisitor<VariableBuilder> {

	public static final VariableVisitor INSTANCE = new VariableVisitor();

	@Override
	public VariableBuilder visitVariableStatement(NimrodFileParser.VariableStatementContext ctx) {
		VariableBuilder vb = new VariableBuilder();
		vb.name(ctx.variableName().getText());
		vb.index(Integer.parseUnsignedInt(ctx.variableIndex().getText(), 10));

		for(NimrodFileParser.VariableValueContext vctx : ctx.variableValue()) {
			String escString = vctx.STRING_LITERAL().getText();
			escString = escString.substring(1, escString.length() - 1);
			try {
				vb.addValue(StringUtils.unescape(escString));
			} catch(EscapeException e) {
				/* ANTLR should catch this before we do. If you get this, check the grammar or CString#unescape(). */
				throw new ParseCancellationException("Invalid escape sequence in string. This should never happen.", e);
			}
		}

		return vb;
	}

	@Override
	public VariableBuilder visitParameterStatement(NimrodFileParser.ParameterStatementContext ctx) {
		VariableBuilder vb = new VariableBuilder();
		vb.name(ctx.parameterName().getText());
		vb.index(-1);

		NimrodFileParser.ParameterLabelContext _ctx = ctx.parameterLabel();
		vb.label(_ctx == null ? null : _ctx.getText());

		if(ctx.parameterType() != null) {
			ParameterType type = parseParameterType(ctx.parameterType());
			vb.addValues(ctx.parameterDomain().accept(new DomainVisitor(type, parameterTypeToString(type))));
		}
		return vb;
	}

	public enum ParameterType {
		Float,
		Integer,
		Text,
		Files
	}

	static ParameterType parseParameterType(NimrodFileParser.ParameterTypeContext ctx) {
		switch(ctx.getText()) {
			case "float":
				return ParameterType.Float;
			case "integer":
				return ParameterType.Integer;
			case "text":
				return ParameterType.Text;
			case "files":
				return ParameterType.Files;
		}

		throw new IllegalArgumentException();
	}

	private static String parameterTypeToString(ParameterType type) {
		switch(type) {
			case Float:
				return "float";
			case Integer:
				return "integer";
			case Text:
				return "text";
			case Files:
				return "files";
		}

		throw new IllegalArgumentException();
	}
}
