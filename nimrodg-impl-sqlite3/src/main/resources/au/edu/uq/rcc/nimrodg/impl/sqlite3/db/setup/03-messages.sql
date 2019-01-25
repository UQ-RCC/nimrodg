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
DROP TABLE IF EXISTS nimrod_master_message_storage;
CREATE TABLE nimrod_master_message_storage(
	id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	operation TEXT NOT NULL CHECK(operation IN ('DELETE', 'INSERT', 'UPDATE')),
	class TEXT NOT NULL CHECK(class IN ('config', 'job')),
	ts INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
	payload TEXT NOT NULL
);