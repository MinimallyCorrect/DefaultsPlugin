package org.minimallycorrect.gradle;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MinecraftVersions {
	public static List<String> getSupportedVersions(String majorVersion) {
		switch (majorVersion) {
			case "1.12":
				return Arrays.asList("1.12.2", "1.12.1", "1.12");
		}

		return Collections.singletonList(majorVersion);
	}

	public static String getMappings(String minecraft) {
		switch (minecraft) {
			case "1.12.2":
				return "snapshot_20171003";
			case "1.12.1":
				return "snapshot_20170624";
			case "1.12":
				return "snapshot_20170617";
			case "1.11.2":
				return "snapshot_20161220";
			case "1.10.2":
				return "snapshot_20160518";
		}

		throw new IllegalArgumentException("Unsupported minecraft version " + minecraft);
	}

	public static String getForge(String minecraft) {
		switch (minecraft) {
			case "1.12.2":
				return "14.23.2.2611";
			case "1.12.1":
				return "14.22.1.2484";
			case "1.12":
				return "14.21.0.2340";
			case "1.11.2":
				return "13.20.0.2216";
			case "1.10.2":
				return "12.18.1.2076";
		}

		throw new IllegalArgumentException("Unsupported minecraft version " + minecraft);
	}
}
