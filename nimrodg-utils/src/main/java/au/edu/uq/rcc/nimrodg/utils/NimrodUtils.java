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
package au.edu.uq.rcc.nimrodg.utils;

import au.edu.uq.rcc.nimrodg.api.Job;
import au.edu.uq.rcc.nimrodg.api.NimrodAPI;
import au.edu.uq.rcc.nimrodg.api.setup.AMQPConfig;
import au.edu.uq.rcc.nimrodg.api.setup.SetupConfig;
import au.edu.uq.rcc.nimrodg.api.setup.TransferConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;

public class NimrodUtils {
	public enum OS {
		Unknown,
		Windows,
		Mac,
		Unix
	}

	public enum Arch {
		Unknown,
		x86,
		x86_64
	}

	public static <T> Optional<T> selectRandomFromContainer(Collection<T> c) {
		return selectRandomFromContainer(c, new Random());
	}

	public static <T> Optional<T> selectRandomFromContainer(Collection<T> c, Random rnd) {
		Objects.requireNonNull(c, "c");
		Objects.requireNonNull(rnd, "rnd");

		if(c.isEmpty()) {
			return Optional.empty();
		}

		return c.stream()
				.skip(rnd.nextInt(c.size()))
				.findFirst();
	}

	public static <T, U> Map<T, List<U>> mapToParent(Collection<U> vals, Function<U, T> mapper) {
		Objects.requireNonNull(vals, "vals");
		return mapToParent(vals.stream(), mapper);
	}

	public static <T, U, V> Map<T, List<V>> mapToParent(Collection<U> vals, Function<U, T> keyMapper, Function<U, V> valueMapper) {
		Objects.requireNonNull(vals, "vals");
		return mapToParent(vals.stream(), keyMapper, valueMapper);
	}

	public static <T, U> Map<T, List<U>> mapToParent(Stream<U> vals, Function<U, T> mapper) {
		return mapToParent(vals, mapper, x -> x);
	}

	public static <T, U, V> Map<T, List<V>> mapToParent(Stream<U> vals, Function<U, T> keyMapper, Function<U, V> valueMapper) {
		Objects.requireNonNull(vals, "vals");
		Objects.requireNonNull(keyMapper, "keyMapper");
		Objects.requireNonNull(valueMapper, "valueMapper");
		Map<T, List<V>> map = new HashMap<>();
		vals.forEach(u -> getOrAddLazy(map, keyMapper.apply(u), tt -> new ArrayList<>()).add(valueMapper.apply(u)));
		return map;
	}

	public static <K, V> V getOrAddLazy(Map<K, V> map, K key, Function<K, V> proc) {
		Objects.requireNonNull(map, "map");
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(proc, "proc");

		V val = map.getOrDefault(key, null);
		if(val == null) {
			val = proc.apply(key);
			map.put(key, val);
		}

		return val;
	}

	public static void deltree(Path path) throws IOException {
		Objects.requireNonNull(path, "path");

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

	public static String buildUniqueJobId(Job job) {
		return String.format("%s/%s", job.getExperiment().getName(), job.getIndex());
	}


	public static OS detectOSFromProperties() {
		String osname = System.getProperty("os.name");
		if(osname == null) {
			return OS.Unknown;
		}

		osname = osname.toLowerCase();
		if(osname.contains("win")) {
			return OS.Windows;
		} else if(osname.contains("mac")) {
			return OS.Mac;
		} else if(osname.contains("nix") || osname.contains("nux") || osname.contains("aix") || osname.endsWith("ix") || osname.endsWith("ux")) {
			return OS.Unix;
		}

		return OS.Unknown;
	}

	public static Arch detectArchFromProperties() {
		String osarch = System.getProperty("os.arch");
		if(osarch == null) {
			return Arch.Unknown;
		}

		osarch = osarch.toLowerCase();
		if(osarch.equals("x86") || osarch.equals("i386") || (osarch.startsWith("i") && osarch.endsWith("86"))) {
			return Arch.x86;
		} else if(osarch.equals("x86_64") || osarch.equals("amd64") || osarch.equals("x64")) {
			return Arch.x86_64;
		}

		return Arch.Unknown;
	}

	public static Map.Entry<OS, Arch> detectPlatform() {
		OS propsOS = detectOSFromProperties();
		Arch propsArch = detectArchFromProperties();

		/* See if it maps "x" to "x.dll" or "libx.so". */
		String xdll = System.mapLibraryName("x");
		if(xdll.equals("x.dll")) {

			int win64Count = 0;

			/* These aren't used anymore, keeping them around for reference.
			{
				int winCount = 0;
				String envOs = System.getenv("OS");
				String windir = System.getenv("WINDIR");
				String systemRoot = System.getenv("SystemRoot");
				String programData = System.getenv("ProgramData");
				String programFiles = System.getenv("ProgramFiles");

				winCount += (envOs != null && envOs.equals("Windows_NT")) ? 1 : 0;
				winCount += (windir != null && !windir.isEmpty()) ? 1 : 0;
				winCount += (systemRoot != null && !systemRoot.isEmpty()) ? 1 : 0;
				winCount += (programData != null && !programData.isEmpty()) ? 1 : 0;
				winCount += (programFiles != null && !programFiles.isEmpty()) ? 1 : 0;

				winCount += (propsOS == OS.Windows) ? 1 : 0;
			} */
			String programFiles86 = System.getenv("ProgramFiles(x86)");
			String programW6432 = System.getenv("ProgramW6432");
			win64Count += (programFiles86 != null && !programFiles86.isEmpty()) ? 1 : 0;
			win64Count += (programW6432 != null && !programW6432.isEmpty()) ? 1 : 0;
			win64Count += (propsArch == Arch.x86_64) ? 1 : 0;

			/* This is pretty-much a guarantee it's Windows. */
			if(win64Count > 0) {
				return Map.entry(OS.Windows, Arch.x86_64);
			} else {
				return Map.entry(OS.Windows, Arch.x86);
			}
		} else if(xdll.equals("libx.so")) {
			/* It's a UNIX system! */
			propsOS = OS.Unix;
		}

		/* Fall back to our os.* properties. */
		return Map.entry(propsOS, propsArch);
	}

	public static NimrodAPI setupApi(NimrodAPI nimrod, SetupConfig cfg) {
		Objects.requireNonNull(nimrod, "nimrod");
		Objects.requireNonNull(cfg, "cfg");

		AMQPConfig amqp = cfg.amqp();
		TransferConfig tx = cfg.transfer();

		nimrod.updateConfig(cfg.workDir(), cfg.storeDir(), amqp.toNimrodUri(), amqp.routingKey(), tx.toNimrodUri());
		cfg.agents().forEach(nimrod::addAgentPlatform);
		cfg.agentMappings().forEach((pair, plat) -> nimrod.mapAgentPosixPlatform(plat, pair));
		cfg.resourceTypes().forEach(nimrod::addResourceType);
		cfg.properties().forEach(nimrod::setProperty);

		return nimrod;
	}

	public static String readEmbeddedFileAsString(Class<?> clazz, String path) {
		return readEmbeddedFileAsString(clazz, path, StandardCharsets.UTF_8);
	}

	public static String readEmbeddedFileAsString(Class<?> clazz, String path, Charset charset) {
		Objects.requireNonNull(charset, "charset");
		return new String(readEmbeddedFile(clazz, path), charset);
	}

	public static byte[] readEmbeddedFile(Class<?> clazz, String path) {
		Objects.requireNonNull(clazz, "clazz");
		Objects.requireNonNull(path, "path");

		try(InputStream is = clazz.getResourceAsStream(path)) {
			if(is == null) {
				throw new IOException("Internal file '" + path + "' doesn't exist in package " + clazz.getPackageName() + ". This is a bug.");
			}

			return is.readAllBytes();
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
