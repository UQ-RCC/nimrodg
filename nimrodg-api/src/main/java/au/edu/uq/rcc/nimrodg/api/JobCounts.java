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
package au.edu.uq.rcc.nimrodg.api;

public class JobCounts {
	public final Experiment experiment;
	public final int completed;
	public final int failed;
	public final int running;
	public final int pending;
	public final int total;

	public JobCounts(Experiment experiment, int completed, int failed, int running, int pending, int total) {
		this.experiment = experiment;
		this.completed = completed;
		this.failed = failed;
		this.running = running;
		this.pending = pending;
		this.total = total;
	}
}