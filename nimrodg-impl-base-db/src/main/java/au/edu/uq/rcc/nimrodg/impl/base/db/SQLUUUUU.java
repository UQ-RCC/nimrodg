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
package au.edu.uq.rcc.nimrodg.impl.base.db;

import au.edu.uq.rcc.nimrodg.api.NimrodException;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * This is really handy boilerplate.
 *
 * @param <X>
 */
public abstract class SQLUUUUU<X extends RuntimeException> implements ISQLBase<X> {

	@Override
	public synchronized <T> T runSQL(SQLReturnProc<T> proc) {
		try {
			return proc.doSQL();
		} catch(SQLException e) {
			throw makeException(e);
		} catch(NimrodException e) {
			throw e;
		} catch(RuntimeException e) {
			throw makeException(new SQLException(e));
		}
	}

	@Override
	public synchronized <T> T runSQLTransaction(SQLReturnProc<T> proc) {
		Connection conn = this.getConnection();
		boolean autocommit = true;
		try {
			autocommit = conn.getAutoCommit();
			conn.setAutoCommit(false);
			T t;
			try {
				t = proc.doSQL();
			} catch(NimrodException e) {
				throw e;
			} catch(RuntimeException e) {
				throw new SQLException(e);
			}
			conn.commit();
			conn.setAutoCommit(autocommit);
			return t;
		} catch(NimrodException e) {
			try {
				conn.rollback();
				conn.setAutoCommit(autocommit);
			} catch(SQLException e2) {
				e2.addSuppressed(e);
				throw makeException(e2);
			}
			throw e;
		} catch(SQLException e) {
			try {
				conn.rollback();
				conn.setAutoCommit(autocommit);
			} catch(SQLException e2) {
				e2.addSuppressed(e);
				throw makeException(e2);
			}

			throw makeException(e);
		}
	}

	@Override
	public synchronized void runSQL(SQLProc proc) {
		runSQL(() -> {
			proc.doSQL();
			return null;
		});
	}

	@Override
	public synchronized void runSQLTransaction(SQLProc proc) {
		runSQLTransaction(() -> {
			proc.doSQL();
			return null;
		});
	}

	protected abstract Connection getConnection();

	protected abstract X makeException(SQLException e);

}
