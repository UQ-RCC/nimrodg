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
package au.edu.uq.rcc.nimrodg.api.utils;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

public class NimrodUtils {

	public static <T> T selectRandomFromContainer(Collection<T> c) {
		if(c.isEmpty()) {
			return null;
		}

		int index = new Random().nextInt(c.size()) + 1;

		Iterator<T> it = c.iterator();
		T r = null;
		for(int i = 0; i < index; ++i) {
			if(!it.hasNext()) {
				return null;
			}
			r = it.next();
		}

		return r;
	}

	public static <T, U> Map<T, List<U>> mapToParent(Collection<U> vals, Function<U, T> mapper) {
		return mapToParent(vals.stream(), mapper);
	}

	public static <T, U, V> Map<T, List<V>> mapToParent(Collection<U> vals, Function<U, T> keyMapper, Function<U, V> valueMapper) {
		return mapToParent(vals.stream(), keyMapper, valueMapper);
	}

	public static <T, U> Map<T, List<U>> mapToParent(Stream<U> vals, Function<U, T> mapper) {
		return mapToParent(vals, mapper, x -> x);
	}

	public static <T, U, V> Map<T, List<V>> mapToParent(Stream<U> vals, Function<U, T> keyMapper, Function<U, V> valueMapper) {
		Map<T, List<V>> map = new HashMap<>();
		vals.forEach(u -> getOrAddLazy(map, keyMapper.apply(u), tt -> new ArrayList<>()).add(valueMapper.apply(u)));
		return map;
	}

	public static <K, V> V getOrAddLazy(Map<K, V> map, K key, Function<K, V> proc) {
		V val = map.getOrDefault(key, null);
		if(val == null) {
			val = proc.apply(key);
			map.put(key, val);
		}

		return val;
	}

	public static void deltree(Path path) throws IOException {
		if(!Files.exists(path)) {
			return;
		}

		if(Files.isDirectory(path)) {
			Files.walkFileTree(path, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					Files.delete(dir);
					return FileVisitResult.CONTINUE;
				}
			});
		} else {
			Files.delete(path);
		}
	}

	@SafeVarargs
	public static <T> T coalesce(T... args) {
		for(T arg : args) {
			if(arg != null) {
				return arg;
			}
		}

		return null;
	}

}
