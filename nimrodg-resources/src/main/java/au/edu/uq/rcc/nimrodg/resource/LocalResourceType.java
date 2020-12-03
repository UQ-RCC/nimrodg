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
package au.edu.uq.rcc.nimrodg.resource;

import au.edu.uq.rcc.nimrodg.api.AgentDefinition;
import au.edu.uq.rcc.nimrodg.api.NimrodURI;
import au.edu.uq.rcc.nimrodg.api.Actuator;
import au.edu.uq.rcc.nimrodg.api.AgentProvider;

import au.edu.uq.rcc.nimrodg.resource.act.LocalActuator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.List;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonStructure;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;
import au.edu.uq.rcc.nimrodg.api.Resource;
import java.nio.file.Path;
import java.util.Map;

public class LocalResourceType extends BaseResourceType {

	@Override
	public String getName() {
		return "local";
	}

	@Override
	public String getDisplayName() {
		return "Local";
	}

	@Override
	protected String getConfigSchema() {
		return "resource_local.json";
	}

	@Override
	protected void addArguments(ArgumentParser parser) {
		super.addArguments(parser);
		parser.addArgument("--parallelism")
				.type(Integer.class)
				.help("Parallelism Count. Omit or set to 0 to autodetect.")
				.required(false)
				.setDefault(0);

		parser.addArgument("--platform")
				.type(String.class)
				.help("Agent Platform String. Omit to autodetect.")
				.required(false);

		parser.addArgument("--capture-output")
				.dest("capture_output")
				.type(String.class)
				.choices("off", "stream", "copy", "inherit")
				.help("Output capturing mode.")
				.setDefault("off");
	}

	@Override
	protected boolean parseArguments(AgentProvider ap, Namespace ns, PrintStream out, PrintStream err, Path[] configDirs, JsonObjectBuilder jb) {
		int para = ns.getInt("parallelism");

		if(para == 0) {
			para = Runtime.getRuntime().availableProcessors();
		}

		jb.add("parallelism", para);

		String plat = ns.get("platform");
		if(plat == null) {
			plat = detectPlatformString(ap);
		}

		if(plat == null) {
			err.printf("Platform detection failed, please specify manually.\n");
			return false;
		}

		jb.add("platform", plat);
		jb.add("capture_mode", ns.getString("capture_output"));
		return true;
	}

	@Override
	public boolean validateConfiguration(AgentProvider ap, JsonStructure _cfg, List<String> errors) {
		if(!super.validateConfiguration(ap, _cfg, errors)) {
			return false;
		}

		String platform = _cfg.asJsonObject().getString("platform");
		if(ap.lookupAgentByPlatform(platform) == null) {
			errors.add("No agents match provided platform.");
			return false;
		}

		return true;
	}

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

	private static String spawnProcessAndReadSingleTrimmedLine(String... args) {
		try {
			Process proc = new ProcessBuilder(args).start();
			int ret = -1;
			while(true) {
				try {
					ret = proc.waitFor();
					break;
				} catch(InterruptedException e) {
					/* nop */
				}
			}

			if(ret != 0) {
				return null;
			}

			try(BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
				return br.readLine().trim();
			}
		} catch(IOException e) {
			return null;
		}
	}

	private static String mapToPlat(OS os, Arch arch, AgentProvider ap) {
		/* No-can-do */
		if(os == OS.Unknown || arch == Arch.Unknown) {
			return null;
		}

		if(os == OS.Mac) {
			/* I have no idea what this should be. */
			return null;
		}

		/* Try common platform strings, there's not else we can do. */
		String[] ss = new String[0];

		if(os == OS.Windows) {
			if(arch == Arch.x86) {
				ss = new String[]{
					"i686-w64-mingw32",
					"i586-w64-mingw32",
					"i486-w64-mingw32",
					"i386-w64-mingw32",
					"i686-pc-windows-msvc",
					"i586-pc-windows-msvc",
					"i486-pc-windows-msvc",
					"i386-pc-windows-msvc"
				};
			} else if(arch == Arch.x86_64) {
				ss = new String[]{
					"x86_64-w64-mingw32",
					"x86_64-pc-windows-msvc"
				};
			}
		} else if(os == OS.Unix) {
			/* Favour musl over glibc ones as they're more likely to work. */
			if(arch == Arch.x86) {
				ss = new String[]{
					"i686-pc-linux-musl",
					"i586-pc-linux-musl",
					"i486-pc-linux-musl",
					"i386-pc-linux-musl",
					"i686-pc-linux-gnu",
					"i586-pc-linux-gnu",
					"i486-pc-linux-gnu",
					"i386-pc-linux-gnu"
				};
			} else if(arch == Arch.x86_64) {
				ss = new String[]{
					"x86_64-pc-linux-musl",
					"x86_64-pc-linux-gnu"
				};
			}
		}

		for(String s : ss) {
			if(ap.lookupAgentByPlatform(s) != null) {
				return s;
			}
		}

		return null;
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

	private static String detectPlatformString(AgentProvider ap) {
		Map.Entry<OS, Arch> plat = detectPlatform();

		if(plat.getKey() == OS.Unix) {
			String unameSystem = spawnProcessAndReadSingleTrimmedLine("uname", "-s");
			String unameMachine = spawnProcessAndReadSingleTrimmedLine("uname", "-m");
			AgentDefinition ai = ap.lookupAgentByPosix(unameSystem, unameMachine);
			if(ai != null) {
				return ai.getPlatformString();
			}
		}

		return mapToPlat(plat.getKey(), plat.getValue(), ap);
	}

	@Override
	public Actuator createActuator(Actuator.Operations ops, Resource node, NimrodURI amqpUri, Certificate[] certs) throws IOException {
		List<String> errors = new ArrayList<>();
		JsonStructure _cfg = node.getConfig();
		if(!validateConfiguration(ops, _cfg, errors)) {
			throw new IOException("Invalid resource configuration");
		}

		JsonObject cfg = _cfg.asJsonObject();

		String _cap = cfg.getString("capture_mode");
		LocalActuator.CaptureMode cap;
		switch(_cap) {
			case "off":
				cap = LocalActuator.CaptureMode.OFF;
				break;
			case "stream":
				cap = LocalActuator.CaptureMode.STREAM;
				break;
			case "copy":
				cap = LocalActuator.CaptureMode.COPY;
				break;
			case "inherit":
				cap = LocalActuator.CaptureMode.INHERIT;
				break;
			default:
				throw new IOException("Invalid capture mode. This is a bug.");

		}
		return new LocalActuator(ops, node, amqpUri, certs, cfg.getInt("parallelism"), cfg.getString("platform"), cap);
	}

}
