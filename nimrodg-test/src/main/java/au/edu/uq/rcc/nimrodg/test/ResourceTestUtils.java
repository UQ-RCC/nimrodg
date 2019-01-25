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

import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import java.util.Collection;
import au.edu.uq.rcc.nimrodg.api.Resource;

public class ResourceTestUtils {

//	static void testTinarooAssigned(NimrodAPI api, Experiment exp, ResourceNode tinaroo, boolean intel, boolean amd) {
//		Collection<? extends ResourceNode> res = api.getAssignedResources(exp);
//
//		ResourceNode.AssignedState state;
//		if(intel && amd) {
//			state = ResourceNode.AssignedState.Assigned;
//		} else if(!intel && !amd) {
//			state = ResourceNode.AssignedState.Unassigned;
//		} else {
//			state = ResourceNode.AssignedState.Partial;
//		}
//		org.junit.Assert.assertEquals(state, api.getResourceAssignmentState(tinaroo, exp));
//
//		{
//			ResourceNode intelNode = tinaroo.resolve("intel");
//			if(intel) {
//				org.junit.Assert.assertEquals(!intel, res.contains(intelNode));
//			}
//
//			org.junit.Assert.assertEquals(intel, res.contains(intelNode.resolve("4gb")));
//			org.junit.Assert.assertEquals(intel, res.contains(intelNode.resolve("2gb")));
//
//			state = intel ? ResourceNode.AssignedState.Assigned : ResourceNode.AssignedState.Unassigned;
//			org.junit.Assert.assertEquals(state, api.getResourceAssignmentState(intelNode, exp));
//			org.junit.Assert.assertEquals(state, api.getResourceAssignmentState(intelNode.resolve("4gb"), exp));
//			org.junit.Assert.assertEquals(state, api.getResourceAssignmentState(intelNode.resolve("2gb"), exp));
//		}
//
//		{
//			ResourceNode amdNode = tinaroo.resolve("amd");
//			if(amd) {
//				org.junit.Assert.assertEquals(!amd, res.contains(amdNode));
//			}
//
//			org.junit.Assert.assertEquals(amd, res.contains(amdNode.resolve("4gb")));
//			org.junit.Assert.assertEquals(amd, res.contains(amdNode.resolve("2gb")));
//
//			state = amd ? ResourceNode.AssignedState.Assigned : ResourceNode.AssignedState.Unassigned;
//			org.junit.Assert.assertEquals(state, api.getResourceAssignmentState(amdNode, exp));
//			org.junit.Assert.assertEquals(state, api.getResourceAssignmentState(amdNode.resolve("4gb"), exp));
//			org.junit.Assert.assertEquals(state, api.getResourceAssignmentState(amdNode.resolve("2gb"), exp));
//		}
//	}

	static void testNectarAssigned(NimrodAPI api, Experiment exp, Resource nectar, boolean expected, int small, int medium, int large) {
		Collection<? extends Resource> res = api.getAssignedResources(exp);

		/* TODO: this */
	}
}
