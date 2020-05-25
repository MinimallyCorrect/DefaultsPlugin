package org.minimallycorrect.gradle;

import java.io.ByteArrayOutputStream;
import java.io.IOError;
import java.io.UnsupportedEncodingException;

import org.gradle.api.Project;

public class Git {
	public static String getBranch(Project project) {
		var out = new ByteArrayOutputStream();
		var result = project.exec(it -> {
			it.setCommandLine("git", "rev-parse", "--abbrev-ref", "HEAD");
			it.setStandardOutput(out);
		});
		if (result.getExitValue() != 0)
			throw new Error("Failed to get branch");
		try {
			return out.toString(DefaultsPlugin.CHARSET.name()).trim();
		} catch (UnsupportedEncodingException e) {
			throw new IOError(e);
		}
	}
}
