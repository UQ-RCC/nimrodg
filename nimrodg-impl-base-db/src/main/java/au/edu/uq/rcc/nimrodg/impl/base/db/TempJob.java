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
package au.edu.uq.rcc.nimrodg.impl.base.db;

import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.Job;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import au.edu.uq.rcc.nimrodg.api.utils.NimrodUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;

public class TempJob {

	public final long id;
	public final long expId;
	public final long jobIndex;
	public final Instant created;
	public final JobAttempt.Status status;
	public final Map<String, String> variables;

	public TempJob(long id, long expId, long jobIndex, Instant created, JobAttempt.Status status, Map<String, String> variables) {
		this.id = id;
		this.expId = expId;
		this.jobIndex = jobIndex;
		this.created = created;
		this.status = status;
		this.variables = Map.copyOf(variables);
	}

	public Impl create(NimrodDBAPI db, Experiment exp) {
		return new Impl(db, exp);
	}

	public class Impl implements Job {

		private final NimrodDBAPI m_DB;
		private final Experiment m_Experiment;
		public final TempJob base;

		private Impl(NimrodDBAPI db, Experiment exp) {
			this.m_DB = db;
			this.m_Experiment = exp;
			this.base = TempJob.this;
		}

		@Override
		public Experiment getExperiment() {
			return m_Experiment;
		}

		@Override
		public long getIndex() {
			return jobIndex;
		}

		@Override
		public Map<String, String> getVariables() {
			return variables;
		}

		@Override
		public Instant getCreationTime() {
			return created;
		}

		@Override
		public Collection<JobAttempt> filterAttempts(EnumSet<JobAttempt.Status> status) {
			return m_DB.runSQL(() -> Collections.unmodifiableCollection(m_DB.filterJobAttempts(this, status)));
		}

		@Override
		public JobAttempt.Status getCachedStatus() {
			return status;
		}

		@Override
		public int hashCode() {
			return Long.hashCode(id);
		}

		@Override
		public boolean equals(Object obj) {
			if(obj == null || !(obj instanceof Impl)) {
				return false;
			}

			Impl job = (Impl)obj;

			return id == job.base.id;
		}

	}
}
