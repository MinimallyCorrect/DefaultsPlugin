package dev.minco.gradle.changelog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Formats the changelog content.
 */
public class ChangelogBuilder {
	public static String extractCommits(String from, String to) throws IOException {
		Process proc = null;
		String output;
		var args = Arrays.asList("git",
			"log",
			"--no-merges",
			"--format=format:- %s by %an",
			from + ".." + to);
		try {
			proc = new ProcessBuilder(args)
				.redirectErrorStream(true)
				.start();
			proc.waitFor(4, TimeUnit.SECONDS);
			output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		} finally {
			if (proc != null) {
				proc.destroy();
			}
		}
		if (proc.exitValue() == 0 && !output.isBlank()) {
			return output;
		}
		throw new RuntimeException("Error running git log. Exit code " + proc.exitValue() + ", output: " + output + ", command ran: " + args);
	}


	public static String formatChangelog(String commits,
										 String version, String previousRev, String currentRev,
										 String githubRepoUrl, String date) {
		return "#### " + date + " - " + version + "\n\n" +
			"[Compare commits](" + githubRepoUrl + "/compare/" + previousRev + "..." + currentRev + ")\n\n" +
			"" + commits;
	}
}
