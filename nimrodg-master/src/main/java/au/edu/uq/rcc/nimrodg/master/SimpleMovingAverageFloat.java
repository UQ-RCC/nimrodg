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

import java.util.Arrays;

public class SimpleMovingAverageFloat {
	private final float[] samples;
	private int count;
	private int write;
	private float sum;

	public SimpleMovingAverageFloat(int sampleCount) {
		this.samples = new float[sampleCount];
		this.count = 0;
		this.write = 0;
		this.sum = 0.0f;
	}

	public float addSample(float f) {
		count = Math.min(count + 1, samples.length);
		sum = sum - samples[write] + f;
		samples[write] = f;

		write = (write + 1) % samples.length;

		return getAverage();
	}

	public float getAverage() {
		if(count == 0) {
			return 0.0f;
		}

		return sum / count;
	}

	public int getSampleCount() {
		return count;
	}

	public void clear() {
		count = 0;
		Arrays.fill(samples, 0.0f);
	}
}
