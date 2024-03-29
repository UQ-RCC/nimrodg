/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2021 The University of Queensland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.edu.uq.rcc.nimrodg.master.sched;

import au.edu.uq.rcc.nimrodg.agent.messages.AgentUpdate;
import au.edu.uq.rcc.nimrodg.api.CommandResult;
import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.Job;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import au.edu.uq.rcc.nimrodg.api.JobAttempt.Status;
import au.edu.uq.rcc.nimrodg.utils.NimrodUtils;
import au.edu.uq.rcc.nimrodg.master.ConfigListener;
import au.edu.uq.rcc.nimrodg.master.JobSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public class DefaultJobScheduler implements JobScheduler {

	private static final Logger LOGGER = LoggerFactory.getLogger(DefaultJobScheduler.class);
	private static final int DEFAULT_BUFFER_SIZE = 1000;
	private static final int DEFAULT_BUFFER_REFILL_THRESHOLD = 100;

	private Operations ops;
	private Experiment exp;

	/* The job queue, to-be-run. */
	private final Queue<Job> incomingJobs;

	private int bufferSize;
	private int bufferThreshold;

	private static class JobInfo {

		public final Job job;
		public int retryCount;
		public final LinkedHashSet<JobAttempt> attempts;

		public JobInfo(Job job) {
			this.job = job;
			this.retryCount = 0;
			this.attempts = new LinkedHashSet<>();
		}

	}

	private final LinkedHashMap<Job, JobInfo> jobInfo;
	private final Set<JobAttempt> runningAttempts;

	private long highestIndex;

	public DefaultJobScheduler() {
		this.ops = null;

		this.incomingJobs = new LinkedList<>();
		this.jobInfo = new LinkedHashMap<>();
		this.runningAttempts = new HashSet<>();

		this.bufferSize = DEFAULT_BUFFER_SIZE;
		this.bufferThreshold = DEFAULT_BUFFER_REFILL_THRESHOLD;

		this.highestIndex = 0;
	}

	private void reset() {
		this.highestIndex = 0;
		this.incomingJobs.clear();
		this.jobInfo.clear();
		this.runningAttempts.clear();
	}

	@Override
	public void setJobOperations(Operations ops) throws IllegalArgumentException {
		if(ops == null || this.ops != null) {
			throw new IllegalArgumentException();
		}
		this.ops = ops;
		this.exp = ops.getExperiment();
		reset();
	}

	@Override
	public void recordAttempts(Collection<JobAttempt> atts, Collection<Job> jobs) {
		if(atts.size() != jobs.size()) {
			throw new IllegalArgumentException();
		}

		this.incomingJobs.removeAll(jobs);

		/* NB: These guys are sets, they'll handle the duplicates themselves. */
		Iterator<JobAttempt> attit = atts.iterator();
		Iterator<Job> jobit = jobs.iterator();
		for(int i = 0; i < atts.size(); ++i) {
			NimrodUtils.getOrAddLazy(this.jobInfo, jobit.next(), JobInfo::new).attempts.add(attit.next());
		}
		this.runningAttempts.addAll(atts);
	}

	@Override
	public void onJobLaunchSuccess(JobAttempt att, UUID agentUuid) {
		//LOGGER.debug("onJobLaunchSuccess: {} on {}", att.getPath(), agentUuid);
		ops.updateJobStarted(att, agentUuid);
	}

	@Override
	public void onJobLaunchFailure(JobAttempt att, UUID agentUuid, Throwable t) {
		//LOGGER.debug("onJobLaunchFailure: {} on {}", att.getPath(), agentUuid);
		ops.updateJobFinished(att, true);
		tickJobAttempt(att);
	}

	@Override
	public void onJobUpdate(JobAttempt att, AgentUpdate au, int numCommands) {
		AgentUpdate.CommandResult_ cr = au.getCommandResult();

		int maxIdx = numCommands - 1;

		ops.recordCommandResult(att, cr.status, cr.index, cr.time, cr.retVal, cr.message, cr.errorCode, au.getAction() == AgentUpdate.Action.Stop);

		JobAttempt.Status status = att.getStatus();

		assert status == Status.RUNNING;

		if(au.getAction() == AgentUpdate.Action.Stop) {
			if(cr.status != CommandResult.CommandResultStatus.SUCCESS) {
				/* A command has failed and caused the job to stop. */
				ops.updateJobFinished(att, true);
			} else {
				/* We've finished successfully. */
				ops.updateJobFinished(att, false);
			}
		}

		tickJobAttempt(att);
	}

	@Override
	public void onJobFailure(JobAttempt att, AgentScheduler.Operations.FailureReason reason) {
		String msg = reason == AgentScheduler.Operations.FailureReason.EXPIRED ? "Agent expired." : "Agent crashed.";
		ops.recordCommandResult(att, CommandResult.CommandResultStatus.ABORTED, -1, 0.0f, 0, msg, 0, true);
		ops.updateJobFinished(att, true);
		tickJobAttempt(att);
	}

	private void purgeJobAttempt(Job job, JobAttempt att) {
		JobInfo sss = jobInfo.get(job);
		sss.attempts.remove(att);
		if(sss.attempts.isEmpty()) {
			jobInfo.remove(job);
		}

		runningAttempts.remove(att);
	}

	private void tickJobAttempt(JobAttempt att) {
		/* Use the job-level status for this. */
		Job j = att.getJob();
		JobInfo stats = jobInfo.get(j);

		Status stat = ops.fetchJobStatus(j);

		if(stat == JobAttempt.Status.COMPLETED) {
			++stats.retryCount;
			LOGGER.info("Job '{}' succeeded on attempt {}!", NimrodUtils.buildUniqueJobId(stats.job), stats.retryCount);
			purgeJobAttempt(stats.job, att);
		} else if(stat == JobAttempt.Status.FAILED) {
			++stats.retryCount;
			if(stats.retryCount > 3) {
				LOGGER.info("Job '{}' exceeded retry count, failing...", NimrodUtils.buildUniqueJobId(stats.job));
				purgeJobAttempt(stats.job, att);
			} else {
				LOGGER.info("Job '{}' failed on attempt {}, rescheduling...", NimrodUtils.buildUniqueJobId(stats.job), stats.retryCount);
				runningAttempts.remove(att);
				stats.attempts.remove(att);
				incomingJobs.offer(stats.job);
			}
		}

	}

	@Override
	public void onJobAdd(Job job) {
		incomingJobs.offer(job);
	}

	@Override
	public void onConfigChange(String key, String oldValue, String newValue) {
		Objects.requireNonNull(key, "key");

		switch(key) {
			case "nimrod.sched.default.job_buf_size":
				bufferSize = ConfigListener.get(newValue, bufferSize, DEFAULT_BUFFER_SIZE, 0, Integer.MAX_VALUE);
				break;

			case "nimrod.sched.default.job_buf_refill_threshold":
				bufferThreshold = ConfigListener.get(newValue, bufferThreshold, DEFAULT_BUFFER_REFILL_THRESHOLD, 0, Integer.MAX_VALUE);
				break;
		}
	}

	@Override
	public boolean tick() {
		//long startTime = System.currentTimeMillis();
		int n = 0;

		/*
		 * For any runs below the job threshold, check for any more jobs.
		 * If no jobs are available, store them as we check use them later.
		 */
		boolean empty = false;
		int cccc = runningAttempts.size() + incomingJobs.size();
		if(cccc < bufferThreshold) {
			Collection<Job> nj = ops.filterJobs(
					exp,
					EnumSet.of(JobAttempt.Status.FAILED, JobAttempt.Status.NOT_RUN),
					highestIndex + 1,
					bufferSize - cccc
			);

			n += nj.size();
			if(!(empty = nj.isEmpty())) {
				highestIndex = nj.stream().mapToLong(Job::getIndex).max().getAsLong();
			}

			incomingJobs.addAll(nj);
		}

		//double taken = (System.currentTimeMillis() - startTime) / 1000.0;
		//LOGGER.trace("Queried {} jobs in {} seconds", n, taken);

		/* Filter the incoming job messages */
		ArrayList<Job> jobQueue = new ArrayList<>(incomingJobs.size());
		jobQueue.addAll(incomingJobs);
		incomingJobs.clear();

		//startTime = System.currentTimeMillis();
		n = jobQueue.size();

		/* FIXME: Keeping the behaviour for now. */
		jobQueue.forEach(j -> LOGGER.info("Scheduling job '{}'", NimrodUtils.buildUniqueJobId(j)));

		/* FIXME: Just schedule everything */
		Collection<JobAttempt> attempts = ops.runJobs(jobQueue);
		recordAttempts(attempts, jobQueue);

		//taken = (System.currentTimeMillis() - startTime) / 1000.0;
		//LOGGER.trace("Created {} attempts in {} seconds", n, taken);

		jobQueue.clear();

		return !jobInfo.isEmpty();
	}

	public static final JobSchedulerFactory FACTORY = DefaultJobScheduler::new;
}
