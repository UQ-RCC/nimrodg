package au.edu.uq.rcc.nimrodg.portal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AddExperiment {
	public final String name;
	public final String planfile;

	@JsonCreator
	public AddExperiment(
			@JsonProperty(value = "name", required = true) String name,
			@JsonProperty(value = "planfile", required = true) String planfile) {
		this.name = name;
		this.planfile = planfile;
	}
}
