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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.Map;
import java.util.Objects;

public class HPCRSSubmitter implements Submitter {
    private static final Logger LOGGER = LoggerFactory.getLogger(HPCRSSubmitter.class);

    private final ResourceClient rc;

    public HPCRSSubmitter(ResourceClient rc) {
        this.rc = Objects.requireNonNull(rc, "rc");
    }

    @Override
    public String submitNimrod(String expName, String account) throws HttpStatusCodeException {
        ResponseEntity<String> resp = rc.executeJob("startexperiment", Map.of(
                "exp_name", expName,
                "account", account
        ));

        /* The above should throw on failure, but I don't trust it to. */
        if(resp.getStatusCode() != HttpStatus.OK) {
            LOGGER.warn("Resource server /startexperiment call succeeded, but returned {}", resp.getStatusCodeValue());
            throw new HttpServerErrorException(resp.getStatusCode());
        }

        if(!resp.hasBody()) {
            LOGGER.warn("Resource server /startexperiment call succeeded, but returned empty body");
            throw new HttpServerErrorException(resp.getStatusCode());
        }

        return Objects.requireNonNull(resp.getBody()).strip();
    }
}
