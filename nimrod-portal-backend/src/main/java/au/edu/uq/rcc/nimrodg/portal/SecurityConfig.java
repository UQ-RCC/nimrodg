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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.resource.IssuerUriCondition;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter  {
	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http.csrf().ignoringAntMatchers("/api/compile");

		http.authorizeRequests()
				.antMatchers("/api/compile").permitAll()
				.requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll()
				.anyRequest().hasAuthority("USER")
				.and()
				.oauth2ResourceServer()
				.jwt()
				.jwtAuthenticationConverter(PortalJwtAuthenticationConverter.INSTANCE);
	}

	/* All of this is edited from OAuth2ResourceServerJwtConfiguration */

	@Value("${spring.security.oauth2.resourceserver.jwt.audience-id}")
	private String audience;

	@Bean
	@ConditionalOnProperty(name = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri")
	JwtDecoder jwtDecoderByJwkKeySetUri(OAuth2ResourceServerProperties properties) {
		OAuth2ResourceServerProperties.Jwt jwtProperties = properties.getJwt();

		NimbusJwtDecoder nimbusJwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwtProperties.getJwkSetUri())
				.jwsAlgorithm(SignatureAlgorithm.from(jwtProperties.getJwsAlgorithm())).build();

		List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
		String issuerUri = jwtProperties.getIssuerUri();
		if(issuerUri != null) {
			validators.add(JwtValidators.createDefaultWithIssuer(issuerUri));
		}

		if(audience != null) {
			final OAuth2Error error = new OAuth2Error("access_denied", String.format(
					"Invalid token does not contain resource id (%s)", audience
			), null);

			validators.add(jwt -> {
				if(!jwt.getAudience().contains(audience)) {
					return OAuth2TokenValidatorResult.failure(error);
				}
				return OAuth2TokenValidatorResult.success();
			});
		}

		nimbusJwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));

		return nimbusJwtDecoder;
	}

	@Bean
	@Conditional(IssuerUriCondition.class)
	JwtDecoder jwtDecoderByIssuerUri(OAuth2ResourceServerProperties properties) {
		/*
		 * Deliberately stopping this:
		 * - I don't want the auth server being down to stop this from starting
		 * - There's no way to add the audience validator in here
		 *   (as JwtDecoderProviderConfigurationUtils) is package private.
		 */
		throw new UnsupportedOperationException("Refusing to allow start without spring.security.oauth2.resourceserver.jwt.jwk-set-uri");
	}
}
