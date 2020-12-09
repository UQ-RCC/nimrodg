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

import au.edu.uq.rcc.nimrodg.api.CommandResult;
import au.edu.uq.rcc.nimrodg.api.JobAttempt;

public class TempCommandResult {

	public final long id;
	public final long attemptId;
	public final CommandResult.CommandResultStatus status;
	public final long commandIndex;
	public final float time;
	public final int retval;
	public final String message;
	public final int errorCode;
	public final boolean stop;

	public TempCommandResult(long id, long attemptId, CommandResult.CommandResultStatus status, long commandIndex, float time, int retval, String message, int errorCode, boolean stop) {
		this.id = id;
		this.attemptId = attemptId;
		this.status = status;
		this.commandIndex = commandIndex;
		this.time = time;
		this.retval = retval;
		this.message = message;
		this.errorCode = errorCode;
		this.stop = stop;
	}

	public Impl create(TempJobAttempt.Impl attempt) {
		return new Impl();
	}

	public class Impl implements CommandResult {
		public final TempCommandResult base;

		private Impl() {
			this.base = TempCommandResult.this;
		}

		@Override
		public CommandResultStatus getStatus() {
			return status;
		}

		@Override
		public long getIndex() {
			return commandIndex;
		}

		@Override
		public float getTime() {
			return time;
		}

		@Override
		public int getReturnValue() {
			return retval;
		}

		@Override
		public String getMessage() {
			return message;
		}

		@Override
		public int getErrorCode() {
			return errorCode;
		}

		@Override
		public boolean stopped() {
			return stop;
		}

		@Override
		public int hashCode() {
			return Long.hashCode(id);
		}

		public boolean equals(Object obj) {
			if(!(obj instanceof Impl)) {
				return false;
			}

			return id == ((TempCommandResult.Impl)obj).base.id;
		}

	}

}
