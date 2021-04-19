/*
 * Nimrod Portal Backend
 * https://github.com/UQ-RCC/nimrodg
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2020 The University of Queensland
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

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PortalJwtAuthenticationConverter extends JwtAuthenticationConverter {

    public static PortalJwtAuthenticationConverter INSTANCE;
    public static AuthorityConverter AUTHORITY_CONVERTER;

    static {
        AUTHORITY_CONVERTER = new AuthorityConverter();
        INSTANCE = new PortalJwtAuthenticationConverter();
        INSTANCE.setJwtGrantedAuthoritiesConverter(AUTHORITY_CONVERTER);
    }

    private static final JwtGrantedAuthoritiesConverter SCOPES = new JwtGrantedAuthoritiesConverter();

    public static class AuthorityConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");

            /* Combine the default SCOPE_* authorities with the realm_access ones. */
            return Stream.concat(
                    SCOPES.convert(jwt).stream(),
                    Optional.ofNullable(realmAccess.get("roles"))
                            .map(o -> ((Collection<?>)o))
                            .orElse(List.of()).stream()
                            .map(String::valueOf)
                            .map(String::toUpperCase)
                            .map(SimpleGrantedAuthority::new)
            ).collect(Collectors.toList());
        }
    }
}
