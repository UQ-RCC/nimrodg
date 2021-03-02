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

import au.edu.uq.rcc.nimrodg.api.utils.XDGDirs;
import au.edu.uq.rcc.nimrodg.resource.LocalResourceType;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AppDirs {
	public final Path configHome;
	public final List<Path> configDirs;

	private AppDirs(Path configHome, List<Path> configDirs) {
		this.configHome = configHome;
		this.configDirs = configDirs;
	}

	public static AppDirs resolve() {
		Map.Entry<LocalResourceType.OS, LocalResourceType.Arch> plat = LocalResourceType.detectPlatform();
		if(plat.getKey() == LocalResourceType.OS.Windows) {
			/* Can't use SHGetKnownFolderPath() unfortunately. */
			Path appData = Paths.get(System.getenv("APPDATA"));
			return new AppDirs(appData.resolve("Nimrod"), List.of());
		}

		/* Use XDG for everything else. */
		XDGDirs xdg = XDGDirs.resolve();
		Path configDir = xdg.configHome.resolve("nimrod");
		return new AppDirs(configDir, xdg.configDirs.stream()
				.map(p -> p.resolve("nimrod"))
				.collect(Collectors.toList())
		);
	}

	public static final AppDirs INSTANCE = resolve();

	public static void main(String[] args) {
		AppDirs dirs = AppDirs.resolve();
		System.out.printf("configHome = %s\n", dirs.configHome);
		for(Path p : dirs.configDirs) {
			System.out.printf("  %s\n", p);
		}
	}
}
