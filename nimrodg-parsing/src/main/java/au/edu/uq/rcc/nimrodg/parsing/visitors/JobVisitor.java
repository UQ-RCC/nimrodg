package au.edu.uq.rcc.nimrodg.parsing.visitors;

import au.edu.uq.rcc.nimrodg.api.utils.run.JobBuilder;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParser;
import au.edu.uq.rcc.nimrodg.parsing.antlr.NimrodFileParserBaseVisitor;
import java.util.stream.Collectors;

public class JobVisitor extends NimrodFileParserBaseVisitor<JobBuilder> {

	public static final JobVisitor INSTANCE = new JobVisitor();

	@Override
	public JobBuilder visitJobEntry(NimrodFileParser.JobEntryContext ctx) {
		JobBuilder jb = new JobBuilder();
		jb.index(Integer.parseUnsignedInt(ctx.jobIndex().getText(), 10));
		jb.addIndices(ctx.jobVarIndex().stream().map(val -> Integer.parseUnsignedInt(val.getText(), 10)).collect(Collectors.toList()));
		return jb;
	}

}
