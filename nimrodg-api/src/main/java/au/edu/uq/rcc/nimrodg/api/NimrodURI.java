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
package au.edu.uq.rcc.nimrodg.api;

import java.net.URI;
import java.util.Objects;

public final class NimrodURI {

	public final URI uri;
	public final String certPath;
	public final Boolean noVerifyPeer;
	public final Boolean noVerifyHost;

	public NimrodURI(URI uri, String certPath, Boolean noVerifyPeer, Boolean noVerifyHost) {
		this.uri = uri;
		this.certPath = certPath;
		this.noVerifyPeer = noVerifyPeer;
		this.noVerifyHost = noVerifyHost;
	}

	public static NimrodURI create(URI uri, String certPath, Boolean noVerifyPeer, Boolean noVerifyHost) {
		NimrodURI nuri = new NimrodURI(uri, certPath, noVerifyPeer, noVerifyHost);

		if(nuri.uri == null && nuri.certPath == null && nuri.noVerifyPeer == null && nuri.noVerifyHost == null) {
			return null;
		}

		return nuri;
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 37 * hash + Objects.hashCode(this.uri);
		hash = 37 * hash + Objects.hashCode(this.certPath);
		hash = 37 * hash + Objects.hashCode(this.noVerifyPeer);
		hash = 37 * hash + Objects.hashCode(this.noVerifyHost);
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if(this == obj) {
			return true;
		}
		if(obj == null) {
			return false;
		}
		if(getClass() != obj.getClass()) {
			return false;
		}
		final NimrodURI other = (NimrodURI)obj;
		if(!Objects.equals(this.certPath, other.certPath)) {
			return false;
		}
		if(!Objects.equals(this.uri, other.uri)) {
			return false;
		}
		if(!Objects.equals(this.noVerifyPeer, other.noVerifyPeer)) {
			return false;
		}
		if(!Objects.equals(this.noVerifyHost, other.noVerifyHost)) {
			return false;
		}
		return true;
	}

}
