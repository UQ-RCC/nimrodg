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

public class SMATests {
	@Test
	public void smaFloat() {
		final float delta = 0.0001f;
		final SimpleMovingAverageFloat sma = new SimpleMovingAverageFloat(10);

		Assert.assertEquals(0.0f, sma.addSample(0), delta);
		Assert.assertEquals(0.5f, sma.addSample(1), delta);
		Assert.assertEquals(1.0f, sma.addSample(2), delta);
		Assert.assertEquals(1.5f, sma.addSample(3), delta);
		Assert.assertEquals(2.0f, sma.addSample(4), delta);
		Assert.assertEquals(2.5f, sma.addSample(5), delta);
		Assert.assertEquals(3.0f, sma.addSample(6), delta);
		Assert.assertEquals(3.5f, sma.addSample(7), delta);
		Assert.assertEquals(4.0f, sma.addSample(8), delta);
		Assert.assertEquals(4.5f, sma.addSample(9), delta);
		Assert.assertEquals(14.5f, sma.addSample(100.0f), delta);
	}

	@Test
	public void smaLong() {
		final SimpleMovingAverageLong sma = new SimpleMovingAverageLong(10);

		Assert.assertEquals(0, sma.addSample(0));
		Assert.assertEquals(0, sma.addSample(1));
		Assert.assertEquals(1, sma.addSample(2));
		Assert.assertEquals(1, sma.addSample(3));
		Assert.assertEquals(2, sma.addSample(4));
		Assert.assertEquals(2, sma.addSample(5));
		Assert.assertEquals(3, sma.addSample(6));
		Assert.assertEquals(3, sma.addSample(7));
		Assert.assertEquals(4, sma.addSample(8));
		Assert.assertEquals(4, sma.addSample(9));
		Assert.assertEquals(14, sma.addSample(100));
	}
}
