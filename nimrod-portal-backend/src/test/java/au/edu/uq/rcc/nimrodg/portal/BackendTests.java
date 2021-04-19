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

import au.edu.uq.rcc.nimrodg.api.utils.StringUtils;
import au.edu.uq.rcc.nimrodg.utils.NimrodUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc /* https://stackoverflow.com/a/47330050 */
public class BackendTests {

    private static final String SCHEMA_SQL = NimrodUtils.readEmbeddedFileAsString(
            PortalServerApplication.class, "schema.sql", StandardCharsets.UTF_8
    );

    @Autowired
    private DataSource dataSource;

    @Autowired
    private NimrodPortalEndpoints npe;

    @MockBean
    private RabbitManagementClient rabbit;

    @MockBean
    private ResourceClient rc;

    @Autowired
    private MockMvc mockMvc;

    private static void nukeUsers(Connection c) throws SQLException {
        ArrayList<String> users = new ArrayList<>();
        try(Statement s = c.createStatement()) {
            try(ResultSet rs = s.executeQuery("SELECT username FROM public.portal_users")) {
                while (rs.next()) {
                    users.add(rs.getString("username"));
                }
            }

            for(String u : users) {
                if(!StringUtils.isIdentifier(u)) {
                    throw new IllegalStateException("Invalid username " + u + ", please investigate.");
                }
                /* NB: Can't use a prepared statement here. */
                s.executeUpdate("DROP SCHEMA IF EXISTS " + u + " CASCADE");
            }
        }
    }

    @BeforeEach
    public void initDatabase() throws SQLException  {
        try(Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try(Statement s = c.createStatement()) {
                s.executeUpdate(SCHEMA_SQL);
            }
            nukeUsers(c);
            c.commit();
        }
    }

    public static Jwt createJwt(String username) {
        /* Based on an actual JWT from Keycloak. */
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .jti(String.valueOf(UUID.randomUUID()))
                .audience(List.of("web-client"))
                .claim("scope", "openid profile address email offline_access phone")
                .claim("realm_access", Map.of("roles", List.of(
                        "offline_access",
                        "uma_authorization",
                        "user"
                )))
                .claim("resource_access", Map.of("account", Map.of("roles", List.of(
                        "manage-account",
                        "manage-account-links",
                        "view-profile"
                ))))
                .claim("address", Map.of())
                .claim("email_verified", false)
                .claim("preferred_username", username)
                .claim("email", String.format("%s@example.com", username))
                .build();
    }

    @Test
    public void provisioningTest() throws Exception {
        Jwt jwt = createJwt("testuser");

        Mockito.when(rc.executeJob(Mockito.eq("setuserconfiguration"), Mockito.any()))
                .thenReturn(ResponseEntity.ok(""));

        Mockito.when(rabbit.addUser(Mockito.eq("testuser"), Mockito.anyString()))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());
        Mockito.when(rabbit.addVHost(Mockito.eq("testuser")))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());
        Mockito.when(rabbit.addPermissions("testuser", "testuser", ".*", ".*", ".*"))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).build());

        /* Provision a user, this should create database accounts, rabbit accounts, etc. */
        mockMvc.perform(
                put("/api/provision/testuser")
                .with(csrf())
                .with(jwt().authorities(PortalJwtAuthenticationConverter.AUTHORITY_CONVERTER).jwt(jwt))
        ).andDo(print()).andExpect(status().isNoContent());

        /* Provision the user again, this should do nothing. */
        Mockito.when(rabbit.addUser(Mockito.eq("testuser"), Mockito.anyString()))
                .thenReturn(ResponseEntity.noContent().build());
        Mockito.when(rabbit.addVHost(Mockito.eq("testuser")))
                .thenReturn(ResponseEntity.noContent().build());
        Mockito.when(rabbit.addPermissions("testuser", "testuser", ".*", ".*", ".*"))
                .thenReturn(ResponseEntity.noContent().build());

        mockMvc.perform(
                put("/api/provision/testuser")
                        .with(csrf())
                        .with(jwt().authorities(PortalJwtAuthenticationConverter.AUTHORITY_CONVERTER).jwt(jwt))
        ).andDo(print()).andExpect(status().isNoContent());
    }
}
