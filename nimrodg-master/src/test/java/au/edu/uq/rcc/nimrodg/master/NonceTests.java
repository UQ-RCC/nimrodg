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
package au.edu.uq.rcc.nimrodg.master;

import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

public class NonceTests {

	@Test
	public void testTimeTravel() {
		Noncer n = new Noncer();
		n.setDuration(Duration.ofSeconds(3));

		Instant now = Instant.EPOCH;

		/* One second after the interval. */
		Assert.assertFalse(n.acceptMessage(now, 0, now.plusSeconds(4)));

		/* One second before the interval. */
		Assert.assertFalse(n.acceptMessage(now, 0, now.minusSeconds(4)));
	}

	@Test
	public void nonceTest() {
		Noncer n = new Noncer();
		Duration dur = Duration.ofSeconds(3);
		n.setDuration(dur);

		long nonce = 0;

		Instant t;

		/* t + 0 */
		t = Instant.EPOCH;
		n.tick(t);
		Assert.assertFalse(n.knowsNonce(nonce));

		/* t + 1 */
		t = t.plusSeconds(1);
		Assert.assertFalse(n.knowsNonce(nonce));
		Assert.assertTrue(n.acceptMessage(t, nonce, t));

		/* t + 2 */
		t = t.plusSeconds(1);
		n.tick(t);
		Assert.assertTrue(n.knowsNonce(nonce));
		Assert.assertFalse(n.acceptMessage(t, nonce, t));

		/* t + 3 */
		t = t.plusSeconds(1);
		n.tick(t);
		Assert.assertTrue(n.knowsNonce(nonce));
		Assert.assertFalse(n.acceptMessage(t, nonce, t));

		/* t + 4 */
		t = t.plusSeconds(1);
		n.tick(t);
		Assert.assertTrue(n.knowsNonce(nonce));
		Assert.assertFalse(n.acceptMessage(t, nonce, t));

		/* t + 5 */
		t = t.plusSeconds(1);
		n.tick(t);
		Assert.assertFalse(n.knowsNonce(nonce));
		Assert.assertFalse(n.acceptMessage(t, nonce, t));

		/* t + 6 */
		t = t.plusSeconds(1);
		n.tick(t);

		{
			/* Set a ping of 100ms */
			n.setPing(100);

			Assert.assertFalse(n.knowsNonce(nonce + 1));
			Assert.assertFalse(n.knowsNonce(nonce + 2));

			/* Should be allowed because of the ping delay. */
			Assert.assertTrue(n.acceptMessage(t, nonce + 1, t.minus(dur)));
			Assert.assertFalse(n.acceptMessage(t, nonce + 2, t.minus(dur).minusMillis(101)));
		}


		/* t + 7 */
		t = t.plusSeconds(1);
		n.tick(t);

		/* t + 8 */
		t = t.plusSeconds(1);
		n.tick(t);

		/* t + 9 */
		t = t.plusSeconds(1);
		n.tick(t);

		/* t + 10 */
		t = t.plusSeconds(1);
		n.tick(t);
	}
}
