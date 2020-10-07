--
-- Nimrod/G
-- https://github.com/UQ-RCC/nimrodg
--
-- SPDX-License-Identifier: Apache-2.0
-- Copyright (c) 2019 The University of Queensland
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

--
-- VOID __create_child_resources_from_definition(BIGINT _parent, JSONB _children)
--
-- This is a recursion helper, don't use manually.
--
-- @param[in] _parent The parent storage id.
-- @param[in] _children A JSONB list of children.
--
CREATE OR REPLACE FUNCTION __create_child_resources_from_definition(_parent BIGINT, _children JSONB) RETURNS VOID AS $$
DECLARE
    dummy BIGINT;
    parent_path nimrod_path;
BEGIN
    IF _children IS NULL THEN
        RETURN;
    END IF;

    SELECT path INTO parent_path FROM nimrod_resources WHERE id = _parent;

    --
    -- Can't use PERFORM with WITH when something's being modified.
    -- HACK: Use COUNT() to get it to an integer and store it in a dummy variable.
    --
    WITH nodes AS(
        SELECT n->>'name' AS name, n->'config' AS config, n->'nodes' AS nodes FROM jsonb_array_elements(_children) AS n
    ), ids AS(
        INSERT INTO nimrod_resources(name, path, parent, config)
        SELECT name, concat_ws('/', parent_path, name), _parent, config FROM nodes AS n
        RETURNING id, name
    )
    SELECT COUNT(__create_child_resources_from_definition(e.id, e.children)) INTO dummy
    FROM (
        SELECT ids.id AS id, nodes.nodes AS children FROM ids, nodes WHERE ids.name = nodes.name
    ) AS e;

END $$ LANGUAGE 'plpgsql';

--
-- BIGINT create_resource_from_definition(JSONB _config)
--
-- Given a resource definition, create the appropriate tables.
--
-- @param[in] _config A JSONB resource definition of the format
-- at https://uq-rcc.github.io/nimrod/schema/resource.definition.json
-- @returns The storage id.
--
CREATE OR REPLACE FUNCTION create_resource_from_definition(_config JSONB) RETURNS BIGINT AS $$
DECLARE
    storage_id BIGINT;
BEGIN
    -- Create the root resource.
    SELECT create_resource(_config->'root'->>'name', _config->>'type', _config->'root'->'config') INTO storage_id;

    -- Now create the child resources
    PERFORM __create_child_resources_from_definition(storage_id, _config->'root'->'nodes');

    RETURN storage_id;
END $$ LANGUAGE 'plpgsql';
