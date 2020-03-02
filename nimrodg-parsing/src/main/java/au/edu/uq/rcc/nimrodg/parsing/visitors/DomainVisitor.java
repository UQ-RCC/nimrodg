package au.edu.uq.rcc.nimrodg.parsing.visitors;

import au.edu.uq.rcc.nimrodg.api.utils.run.suppliers.ValueSupplier;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParser;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParserBaseVisitor;
import au.edu.uq.rcc.nimrodg.parsing.visitors.VariableVisitor.ParameterType;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DomainVisitor extends NimrodFileParserBaseVisitor<List<String>> {

	private final ParameterType type;
	private final String typeString;

	public DomainVisitor(ParameterType type, String typeString) {
		this.type = type;
		this.typeString = typeString;
	}

	@Override
	public List<String> visitDomainDefault(NimrodFileParser.DomainDefaultContext ctx) {
		String val = ctx.variableValue().accept(LiteralVisitor.INSTANCE);

		/* Do some basic validation */
		switch(type) {
			case Float:
				try {
					Double.parseDouble(val);
				} catch(NumberFormatException e) {
					throw new ParseCancellationException(String.format("'%s' parameter has non-%s default value.", typeString, typeString));
				}
				break;
			case Integer:
				try {
					Long.parseLong(val, 10);
				} catch(NumberFormatException e) {
					throw new ParseCancellationException(String.format("'%s' parameter has non-%s default value.", typeString, typeString));
				}
				break;
			case Text:
				/* Anything's good with text */
				break;
			default:
				throw new ParseCancellationException(String.format("'default' domain not valid with parameter type '%s'", typeString));
		}

		return ValueSupplier.createDefaultSupplier(val)
				.stream().collect(Collectors.toList());
	}

	@Override
	public List<String> visitDomainRange(NimrodFileParser.DomainRangeContext ctx) {
		switch(type) {
			case Integer:
				return buildIntegerRangeDomain(ctx);
			case Float:
				return buildFloatRangeDomain(ctx);
			default:
				throw new ParseCancellationException(String.format("'range' domain not valid with parameter type '%s'", typeString));
		}
	}

	private static List<String> buildIntegerRangeDomain(NimrodFileParser.DomainRangeContext ctx) {
		List<NimrodFileParser.NumberContext> nos = ctx.number();

		/* These may be decimals, so see if the integer rule explicitly was matched. */
		NimrodFileParser.IntegerContext _start = nos.get(0).integer();
		if(_start == null) {
			throw new ParseCancellationException("'range' domain has non-integer start");
		}
		long start = Long.parseLong(_start.getText(), 10);

		NimrodFileParser.IntegerContext _stop = nos.get(1).integer();
		if(_stop == null) {
			throw new ParseCancellationException("'range' domain has non-integer stop");
		}
		long end = Long.parseLong(_stop.getText(), 10);

		int step;
		if(ctx.POINTS() != null) {
			/* Generate uniformly-spaced points */
			int npoints = Integer.parseInt(ctx.positiveInteger().getText(), 10);
			step = (int)Math.max(0.0, (end - start) / (double)npoints);
		} else if(ctx.STEP() != null) {
			/* Use the step value to generate points */
			NimrodFileParser.PositiveIntegerContext ictx = ctx.positiveNumber().positiveInteger();
			if(ictx == null) {
				throw new ParseCancellationException("'range' domain has non-integer step");
			}
			step = Integer.parseInt(ictx.getText(), 10);
		} else {
			/* If not specified, default to a step size of 1. */
			step = 1;
		}

		return ValueSupplier.createIntegerRangeStepSupplier(start, end, step)
				.stream().collect(Collectors.toList());
	}

	private static List<String> buildFloatRangeDomain(NimrodFileParser.DomainRangeContext ctx) {
		/* Every number is a decimal! */
		List<NimrodFileParser.NumberContext> nos = ctx.number();

		double start = Double.parseDouble(nos.get(0).getText());
		double end = Double.parseDouble(nos.get(1).getText());

		double step;
		if(ctx.POINTS() != null) {
			int npoints = Integer.parseInt(ctx.positiveInteger().getText(), 10);
			step = Math.max(0.0, (end - start) / (double)npoints);
		} else if(ctx.STEP() != null) {
			step = Double.parseDouble(ctx.positiveNumber().getText());
		} else {
			step = 1.0;
		}

		return ValueSupplier.createFloatRangeStepSupplier(start, end, step)
				.stream().collect(Collectors.toList());
	}

	@Override
	public List<String> visitDomainRandom(NimrodFileParser.DomainRandomContext ctx) {
		switch(type) {
			case Integer:
				return buildIntegerRandomDomain(ctx);
			case Float:
				return buildFloatRandomDomain(ctx);
			default:
				throw new ParseCancellationException(String.format("'random' domain not valid with parameter type '%s'", typeString));
		}
	}

	private static List<String> buildIntegerRandomDomain(NimrodFileParser.DomainRandomContext ctx) {
		List<NimrodFileParser.NumberContext> nos = ctx.number();

		/* These may be decimals, so see if the integer rule explicitly was matched. */
		NimrodFileParser.IntegerContext _start = nos.get(0).integer();
		if(_start == null) {
			throw new ParseCancellationException("'random' domain has non-integer start");
		}
		long start = Long.parseLong(_start.getText(), 10);

		NimrodFileParser.IntegerContext _stop = nos.get(1).integer();
		if(_stop == null) {
			throw new ParseCancellationException("'random' domain has non-integer stop");
		}
		long end = Long.parseLong(_stop.getText(), 10);

		int count = 1;
		NimrodFileParser.PositiveIntegerContext ictx = ctx.positiveInteger();
		if(ictx != null) {
			count = Integer.parseUnsignedInt(ictx.getText(), 10);
		}

		return ValueSupplier.createIntegerRandomSupplier(start, end, count)
				.stream().collect(Collectors.toList());
	}

	private static List<String> buildFloatRandomDomain(NimrodFileParser.DomainRandomContext ctx) {
		List<NimrodFileParser.NumberContext> nos = ctx.number();

		double start = Double.parseDouble(nos.get(0).getText());
		double end = Double.parseDouble(nos.get(1).getText());

		int count = 1;
		NimrodFileParser.PositiveIntegerContext ictx = ctx.positiveInteger();
		if(ictx != null) {
			count = Integer.parseUnsignedInt(ictx.getText(), 10);
		}

		return ValueSupplier.createFloatRandomSupplier(start, end, count)
				.stream().collect(Collectors.toList());
	}

	@Override
	public List<String> visitDomainAnyof(NimrodFileParser.DomainAnyofContext ctx) {
		List<String> vals = ctx.variableValue()
				.stream()
				.map(vv -> vv.accept(LiteralVisitor.INSTANCE))
				.collect(Collectors.toList());

		/* Do some validations */
		switch(type) {
			case Text:
				/* Everything's valid text. */
				break;
			case Integer: {
				long i = 0;
				for(String s : vals) {
					try {
						Long.parseLong(s, 10);
					} catch(NumberFormatException e) {
						throw new ParseCancellationException(String.format("'anyof' domain has non-integer value at index %d", i));
					}
					++i;
				}
				break;
			}
			case Float: {
				long i = 0;
				for(String s : vals) {
					try {
						Double.parseDouble(s);
					} catch(NumberFormatException e) {
						throw new ParseCancellationException(String.format("'anyof' domain has non-float value at index %d", i));
					}
					++i;
				}
				break;
			}
			case Files: {
				List<String> matched = new ArrayList<>();
				/* I really hate doing this here */
				FileSystem fs = FileSystems.getDefault();
				Path cwd = fs.getPath(".");
				for(String s : vals) {
					PathMatcher globMatcher;
					try {
						String pattern = String.format("glob:%s", s);
						globMatcher = fs.getPathMatcher(pattern);
					} catch(RuntimeException e) {
						/* Invalid pattern, either user error or not actually a glob. */
						matched.add(s);
						continue;
					}

					GlobMatchWalker walker = new GlobMatchWalker(globMatcher, cwd);
					try {
						Files.walkFileTree(cwd, walker);
					} catch(IOException e) {
						matched.add(s);
						continue;
					}
					/* Each path is relative to cwd, so just toString() it. */
					walker.getPathList()
							.stream()
							.map(Path::toString)
							.filter(ss -> !ss.isEmpty())
							.forEach(matched::add);
				}
				vals = matched;
				break;
			}
			default:
				throw new ParseCancellationException(String.format("'anyof' domain not valid with parameter type '%s'", typeString));
		}

		return ValueSupplier.createStringSuppliedSupplier(vals)
				.stream().collect(Collectors.toList());
	}
}
