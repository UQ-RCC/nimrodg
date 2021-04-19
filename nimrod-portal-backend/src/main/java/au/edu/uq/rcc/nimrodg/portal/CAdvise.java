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

import au.edu.uq.rcc.nimrodg.api.NimrodException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.sql.SQLException;

@ControllerAdvice
public class CAdvise extends ResponseEntityExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(CAdvise.class);

	@ExceptionHandler(SQLException.class)
	public ResponseEntity<Void> handleSqlException(SQLException e) {
		LOGGER.error("Database Error", e);
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
	}

	@ExceptionHandler(NimrodException.class)
	public ResponseEntity<Void> handleNimrodException(NimrodException e) {
		if(e instanceof NimrodException.DbError) {
			return handleSqlException(((NimrodException.DbError)e).sql);
		}

		LOGGER.error("Nimrod Error", e);
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
	}


	@SuppressWarnings("NullableProblems")
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers, HttpStatus status, WebRequest request) {

		if(HttpStatus.INTERNAL_SERVER_ERROR.equals(status)) {
			status = HttpStatus.SERVICE_UNAVAILABLE;
		}

		return ResponseEntity.status(status).headers(headers).build();
	}
}
