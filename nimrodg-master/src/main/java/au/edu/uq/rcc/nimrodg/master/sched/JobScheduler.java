/*
 * Nimrod/G
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2019 The University of Queensland
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
import au.edu.uq.rcc.nimrodg.master.ConfigListener;
import au.edu.uq.rcc.nimrodg.master.sched.AgentScheduler.Operations.FailureReason;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.UUID;

public interface JobScheduler extends ConfigListener {

	public interface Operations {

		Experiment getExperiment();

		Collection<Job> filterJobs(Experiment exp, EnumSet<JobAttempt.Status> status, long start, int limit);

		JobAttempt.Status fetchJobStatus(Job j);

		Collection<JobAttempt> runJobs(Collection<Job> jobs);

		void cancelJob(JobAttempt att);

		void updateExperimentState(Experiment.State state);

		/**
		 * Mark the given job attempt as started.
		 *
		 * @param att
		 * @param agentUuid
		 */
		void updateJobStarted(JobAttempt att, UUID agentUuid);

		/**
		 * Mark the given job as finished.
		 *
		 * @param att
		 * @param failed
		 */
		void updateJobFinished(JobAttempt att, boolean failed);

		void recordCommandResult(JobAttempt att, CommandResult.CommandResultStatus status, long index, float time, int retval, String message, int errcode, boolean stop);
	}

	/**
	 * Set the job operations instance. This may only set once.
	 *
	 * @param ops The operations instance.
	 * @throws IllegalArgumentException If ops is null or this function has already been called with a non-null
	 * argument.
	 */
	void setJobOperations(Operations ops) throws IllegalArgumentException;

	/**
	 * Record job attempts without actually launching them. This should only be used for state recovery purposes.
	 * @param atts The collection of job attempts.
	 * @param jobs The collection of jobs.
	 */
	void recordAttempts(Collection<JobAttempt> atts, Collection<Job> jobs);

	void onJobAdd(Job job);

	/**
	 * Called when a job attempt has been successfully launched.
	 *
	 * @param att The {@link JobAttempt} instance.
	 * @param agentUuid The UUID of the agent the job has been launched on.
	 */
	void onJobLaunchSuccess(JobAttempt att, UUID agentUuid);

	/**
	 * Called when a job attempt failed to launch.
	 *
	 * @param att The {@link JobAttempt} instance.
	 * @param agentUuid The UUID of the agent the job failed to launch on. If this is null, this indicates a job
	 * resolution failure, an underlying issue with the job that wasn't detected during compilation.
	 *
	 * @param t The exception that caused the launch to fail.
	 */
	void onJobLaunchFailure(JobAttempt att, UUID agentUuid, Throwable t);

	/**
	 * Called when an agent has reported a job update.
	 *
	 * @param att The {@link JobAttempt} instance.
	 * @param au The update message sent by the agent.
	 * @param numCommands The number of commands the job has.
	 */
	void onJobUpdate(JobAttempt att, AgentUpdate au, int numCommands);

	/**
	 * Called when a job failed due to an agent dying.
	 *
	 * @param att The {@link JobAttempt} instance.
	 * @param reason The reason for failure.
	 */
	void onJobFailure(JobAttempt att, FailureReason reason);

	boolean tick();
}
