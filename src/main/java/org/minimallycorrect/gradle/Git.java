package org.minimallycorrect.gradle;

import lombok.SneakyThrows;
import lombok.val;
import org.gradle.api.Project;

import java.io.ByteArrayOutputStream;

public class Git {
	@SneakyThrows
	public static String getBranch(Project project) {
		val out = new ByteArrayOutputStream();
		val result = project.exec(it -> {
			it.setCommandLine("git", "rev-parse", "--abbrev-ref", "HEAD");
			it.setStandardOutput(out);
		});
		if (result.getExitValue() != 0)
			throw new Error("Failed to get branch");
		return out.toString(DefaultsPlugin.CHARSET.name()).trim();
	}
}
