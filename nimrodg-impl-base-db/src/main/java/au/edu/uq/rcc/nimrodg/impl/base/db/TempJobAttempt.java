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
package au.edu.uq.rcc.nimrodg.impl.base.db;

import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import java.time.Instant;
import java.util.UUID;

public class TempJobAttempt {

	public final long id;
	public final long jobId;
	public final UUID uuid;
	public final JobAttempt.Status status;
	public final Instant creationTime;
	public final Instant startTime;
	public final Instant finishTime;
	public final UUID agentUuid;

	public TempJobAttempt(long id, long jobId, UUID uuid, JobAttempt.Status status, Instant creationTime, Instant startTime, Instant finishTime, UUID agentUuid) {
		this.id = id;
		this.jobId = jobId;
		this.uuid = uuid;
		this.status = status;
		this.creationTime = creationTime;
		this.startTime = startTime;
		this.finishTime = finishTime;
		this.agentUuid = agentUuid;
	}

	public Impl create(NimrodDBAPI db, TempJob.Impl job) {
		return new Impl(db, job);
	}

	public class Impl implements JobAttempt {

		private final NimrodDBAPI db;
		private final TempJob.Impl job;

		public final TempJobAttempt base;

		private Impl(NimrodDBAPI db, TempJob.Impl job) {
			this.db = db;
			this.job = job;
			this.base = TempJobAttempt.this;
		}

		@Override
		public TempJob.Impl getJob() {
			return job;
		}

		@Override
		public UUID getUUID() {
			return uuid;
		}

		@Override
		public Status getStatus() {
			return db.runSQL(() -> db.getJobAttempt(this)).status;
		}

		@Override
		public Instant getCreationTime() {
			return creationTime;
		}

		@Override
		public Instant getStartTime() {
			return db.runSQL(() -> db.getJobAttempt(this)).startTime;
		}

		@Override
		public Instant getFinishTime() {
			return db.runSQL(() -> db.getJobAttempt(this)).finishTime;
		}

		@Override
		public UUID getAgentUUID() {
			return db.runSQL(() -> db.getJobAttempt(this)).agentUuid;
		}

		@Override
		public boolean equals(Object obj) {
			if(obj == null || !(obj instanceof Impl)) {
				return false;
			}

			return id == ((Impl)obj).base.id;
		}

		@Override
		public int hashCode() {
			return Long.hashCode(id);
		}
	}
}
