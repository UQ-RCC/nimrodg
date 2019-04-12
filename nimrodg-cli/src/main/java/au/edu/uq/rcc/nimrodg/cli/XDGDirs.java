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
package au.edu.uq.rcc.nimrodg.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/* https://specifications.freedesktop.org/basedir-spec/basedir-spec-latest.html */
public class XDGDirs {

	public final Path dataHome;
	public final Path configHome;
	public final List<Path> dataDirs;
	public final List<Path> configDirs;
	public final Path cacheHome;

	private XDGDirs(Path dataHome, Path configHome, List<Path> dataDirs, List<Path> configDirs, Path cacheHome) {
		this.dataHome = dataHome;
		this.configHome = configHome;
		this.dataDirs = Collections.unmodifiableList(dataDirs);
		this.configDirs = Collections.unmodifiableList(configDirs);
		this.cacheHome = cacheHome;
	}

	private static Path resolveHome() {
		String home = System.getenv("HOME");
		if(home == null || home.isEmpty()) {
			/* Just fall back to Java's home here */
			home = System.getProperty("user.home");
		}

		return Paths.get(home);
	}

	private static Path resolveDataHome(Path home) {
		/* $XDG_DATA_HOME defines the base directory relative to which user specific data files should be stored. */
		String env = System.getenv("XDG_DATA_HOME");
		if(env != null && !env.isEmpty()) {
			return Paths.get(env);
		}

		/* If $XDG_DATA_HOME is either not set or empty, a default equal to $HOME/.local/share should be used. */
		return home.resolve(".local/share");
	}

	private static Path resolveConfigHome(Path home) {
		/*
		 * $XDG_CONFIG_HOME defines the base directory relative to which user specific
		 * configuration files should be stored.
		 */
		String env = System.getenv("XDG_CONFIG_HOME");
		if(env != null && !env.isEmpty()) {
			return Paths.get(env);
		}

		/* If $XDG_CONFIG_HOME is either not set or empty, a default equal to $HOME/.config should be used. */
		return home.resolve(".config");
	}

	private static Path resolveCacheHome(Path home) {
		/*
		 * $XDG_CACHE_HOME defines the base directory relative to which user specific non-essential
		 * data files should be stored.
		 */
		String env = System.getenv("XDG_CACHE_HOME");
		if(env != null && !env.isEmpty()) {
			return Paths.get(env);
		}

		/* If $XDG_CACHE_HOME is either not set or empty, a default equal to $HOME/.cache should be used. */
		return home.resolve(".cache");
	}

	private static List<Path> resolveDataDirs() {
		/*
		 * $XDG_DATA_DIRS defines the preference-ordered set of base directories to search
		 * for data files in addition to the $XDG_DATA_HOME base directory.
		 * The directories in $XDG_DATA_DIRS should be seperated with a colon ':'. 
		 */
		String env = System.getenv("XDG_DATA_DIRS");
		if(env == null || env.isEmpty()) {
			/*
			 * If $XDG_DATA_DIRS is either not set or empty, a value equal to /usr/local/share/:/usr/share/
			 * should be used.
			 */
			env = "/usr/local/share/:/usr/share/";
		}

		return Arrays.stream(env.split(":")).distinct().map(p -> Paths.get(p)).collect(Collectors.toList());
	}

	private static List<Path> resolveConfigDirs() {
		/*
		 * $XDG_CONFIG_DIRS defines the preference-ordered set of base directories to search
		 * for configuration files in addition to the $XDG_CONFIG_HOME base directory.
		 * The directories in $XDG_CONFIG_DIRS should be seperated with a colon ':'. 
		 */
		String env = System.getenv("XDG_CONFIG_DIRS");
		if(env == null || env.isEmpty()) {
			/* If $XDG_CONFIG_DIRS is either not set or empty, a value equal to /etc/xdg should be used. */
			env = "/etc/xdg";
		}

		return Arrays.stream(env.split(":")).distinct().map(p -> Paths.get(p)).collect(Collectors.toList());
	}

	public static final XDGDirs INSTANCE = resolve();

	public static XDGDirs resolve() {
		Path home = resolveHome();
		return new XDGDirs(
				resolveDataHome(home),
				resolveConfigHome(home),
				resolveDataDirs(),
				resolveConfigDirs(),
				resolveCacheHome(home)
		);
	}

	public static void main(String[] args) {
		XDGDirs xdg = XDGDirs.resolve();
		System.out.printf("dataHome   = %s\n", xdg.dataHome);
		System.out.printf("configHome = %s\n", xdg.configHome);
		System.out.printf("dataDirs   =\n");
		for(Path p : xdg.dataDirs) {
			System.out.printf("             %s\n", p);
		}
		System.out.printf("configDirs =\n");
		for(Path p : xdg.configDirs) {
			System.out.printf("             %s\n", p);
		}
		System.out.printf("cacheHome  = %s\n", xdg.cacheHome);
	}
}
