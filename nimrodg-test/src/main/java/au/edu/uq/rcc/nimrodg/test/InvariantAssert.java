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
package au.edu.uq.rcc.nimrodg.test;

import au.edu.uq.rcc.nimrodg.api.JobAttempt;
import junit.framework.Assert;

public class InvariantAssert {

	public static void assertJobAttempt(JobAttempt att) {
		Assert.assertNotNull(att.getJob());

		switch(att.getStatus()) {
			case NOT_RUN:
				Assert.assertNull(att.getStartTime());
				Assert.assertNull(att.getFinishTime());
				Assert.assertNull(att.getAgentUUID());
				break;
			case RUNNING:
				Assert.assertNotNull(att.getStartTime());
				Assert.assertNull(att.getFinishTime());
				Assert.assertNotNull(att.getAgentUUID());
				break;
			case FAILED:
			case COMPLETED:
				Assert.assertNotNull(att.getStartTime());
				Assert.assertNotNull(att.getFinishTime());
				Assert.assertNotNull(att.getAgentUUID());
				Assert.assertTrue(att.getFinishTime().equals(att.getStartTime()) || att.getFinishTime().isAfter(att.getStartTime()));
				break;
		}
	}
}
