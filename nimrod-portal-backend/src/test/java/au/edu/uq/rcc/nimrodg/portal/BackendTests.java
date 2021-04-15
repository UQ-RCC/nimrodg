/*
 * Nimrod Portal Backend
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
package au.edu.uq.rcc.nimrodg.portal;

import au.edu.uq.rcc.nimrodg.utils.NimrodUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@SpringBootTest
public class BackendTests {

    private static final String SCHEMA_SQL = NimrodUtils.readEmbeddedFileAsString(
            PortalServerApplication.class, "schema.sql", StandardCharsets.UTF_8
    );

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    public void initDatabase() throws SQLException  {
        try(Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try(Statement s = c.createStatement()) {
                s.executeUpdate(SCHEMA_SQL);
            }
            c.commit();
        }
    }

    @Test
    public void contextStarts() {

    }
}
