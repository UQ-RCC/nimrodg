package au.edu.uq.rcc.nimrodg.master;

import au.edu.uq.rcc.nimrodg.api.Job;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;

public interface JobMaker {
	interface Operations {
		Collection<Job> addJobs(Collection<Map<String, String>> values);
		Collection<Job> filterJobs(EnumSet<JobAttempt.Status> status, long start, int limit);
	}

	void tick();

	void onJobFinish(Job job, JobAttempt att);
}
