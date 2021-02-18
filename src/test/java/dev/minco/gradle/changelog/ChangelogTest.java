package dev.minco.gradle.changelog;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class ChangelogTest {
	@Test
	public void testCommitExtraction() throws IOException {
		Assumptions.assumeTrue(System.getenv("CI") == null, "Running on CI, full history isn't available");

		var result = ChangelogBuilder.extractCommits("5402843f1e78c477f10c6", "32fa725a96484402d0");
		Assertions.assertEquals("- Remove bintray from CI workflow by Luna\n" +
			"- Remove bintray support from plugin by Luna", result);
	}
}
