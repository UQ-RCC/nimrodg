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

import au.edu.uq.rcc.nimrodg.api.Experiment;
import au.edu.uq.rcc.nimrodg.api.NimrodException;

import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;
import javax.json.JsonStructure;
import au.edu.uq.rcc.nimrodg.agent.AgentState;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.ResourceType;
import au.edu.uq.rcc.nimrodg.api.ResourceTypeInfo;
import java.util.Map;
import au.edu.uq.rcc.nimrodg.api.Resource;
import java.util.Optional;

/**
 * Everything you could ever need to ever do ever with resources.
 *
 * @param <T>
 * @param <E>
 * @param <A>
 * @param <X>
 */
public interface ResourceFunctions<T extends Resource, E extends Experiment, A extends AgentState, X extends Exception> {

	Collection<TempResourceType> getResourceTypeInfo() throws X;

	Optional<TempResourceType> getResourceTypeInfo(String name) throws X;

	TempResourceType addResourceTypeInfo(String name, String clazz) throws X;

	boolean deleteResourceTypeInfo(String name) throws X;

	Optional<T> getResource(String path) throws X;

	void deleteResource(T node) throws X;

	Collection<T> getResources() throws X;

	T addResource(String name, String type, JsonStructure config, NimrodURI amqpUri, NimrodURI txUri) throws X;

	TempResourceType getResourceImplementation(T node) throws X;

	//-----------------ASSIGNMENTS-----------------//
	Collection<T> getAssignedResources(E exp) throws X;

	boolean assignResource(T node, E exp, NimrodURI txUri) throws X;

	boolean unassignResource(T node, E exp) throws X;

	Optional<NimrodURI> getAssignmentStatus(T node, E exp) throws X;

	//-----------------CAPABILITIES----------------//
	boolean isResourceCapable(T node, E exp) throws X;

	boolean addResourceCaps(T node, E exp) throws X;

	boolean removeResourceCaps(T node, E exp) throws X;

	//-----------------AGENTS----------------------//
	Optional<A> getAgentInformationByUUID(UUID uuid) throws X;

	Optional<T> getAgentResource(UUID uuid) throws X;

	Collection<A> getResourceAgentInformation(T node) throws X;

	AgentState addAgent(T node, AgentState agent) throws X;

	void updateAgent(AgentState agent) throws X;

	boolean addAgentPlatform(String platformString, Path path) throws X;

	boolean deleteAgentPlatform(String platformString) throws X;

	boolean mapAgentPosixPlatform(String platformString, String system, String machine) throws X;

	boolean unmapAgentPosixPlatform(String system, String machine) throws X;
}
