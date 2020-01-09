package org.minimallycorrect.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import lombok.val;

import org.gradle.api.Project;
import org.shipkit.gradle.configuration.ShipkitConfiguration;
import org.shipkit.internal.gradle.java.ShipkitJavaPlugin;
import org.shipkit.internal.gradle.versionupgrade.CiUpgradeDownstreamPlugin;
import org.shipkit.internal.gradle.versionupgrade.UpgradeDependencyPlugin;
import org.shipkit.internal.gradle.versionupgrade.UpgradeDownstreamExtension;
import org.shipkit.internal.version.Version;

import com.jfrog.bintray.gradle.BintrayExtension;
import com.jfrog.bintray.gradle.BintrayPlugin;

public class ShipkitExtensions {
	static void initShipkit(DefaultsPlugin defaultsPlugin, Project project) throws IOException {
		val shouldApplyShipKit = defaultsPlugin.shouldApplyShipKit();
		if (shouldApplyShipKit) {
			val shipkitGradle = project.file("gradle/shipkit.gradle");
			if (!shipkitGradle.exists()) {
				if (!shipkitGradle.getParentFile().isDirectory() && !shipkitGradle.getParentFile().mkdirs())
					throw new IOException("Failed to create directory for " + shipkitGradle);
				try (val is = defaultsPlugin.getClass().getResourceAsStream("/shipkit/shipkit.gradle")) {
					Files.copy(is, shipkitGradle.toPath());
				}
			}
			val githubRepo = defaultsPlugin.getGithubRepo();
			project.getExtensions().add("minimallyCorrectDefaultsShipkit", (Callable<Void>) () -> {
				val configuration = project.getExtensions().getByType(ShipkitConfiguration.class);
				configuration.getGitHub().setRepository(githubRepo);
				configuration.getGitHub().setReadOnlyAuthToken("bf61e48ac43dbad4d4a63ff664f5f9446adaa9c5");
				configuration.getGit().setCommitMessagePostfix("[ci skip-release]");
				configuration.getReleaseNotes().setIgnoreCommitsContaining(Arrays.asList("[ci skip]", "[ci skip-release]"));

				if (defaultsPlugin.settings.minecraft != null) {
					configuration.getGit().setTagPrefix('v' + defaultsPlugin.settings.minecraft + '_');
					configuration.getGit().setReleasableBranchRegex('^' + Pattern.quote(defaultsPlugin.settings.minecraft) + "(/|$)");
				}

				return null;
			});
			project.getPlugins().apply(ShipkitJavaPlugin.class);

			if (defaultsPlugin.settings.minecraft != null)
				project.setVersion(defaultsPlugin.settings.minecraft + '-' + project.getVersion().toString());

			project.allprojects(it -> {
				it.getPlugins().apply(BintrayPlugin.class);
				val bintray = it.getExtensions().getByType(BintrayExtension.class);
				bintray.setUser("nallar");
				bintray.setKey(System.getenv("BINTRAY_KEY"));
				val pkg = bintray.getPkg();
				pkg.setName(project.getName());
				pkg.setRepo("minimallycorrectmaven");
				pkg.setUserOrg("minimallycorrect");
				pkg.setVcsUrl(defaultsPlugin.getVcsUrl());
				pkg.setGithubReleaseNotesFile(DefaultsPlugin.RELEASE_NOTES_PATH);
				pkg.setWebsiteUrl(defaultsPlugin.getWebsiteUrl());
				if (defaultsPlugin.settings.licenses == null)
					throw new IllegalArgumentException("Must set settings.licenses when shipkit is enabled");
				pkg.setLicenses(defaultsPlugin.settings.licenses);
				if (defaultsPlugin.settings.labels == null)
					throw new IllegalArgumentException("Must set labels when shipkit is enabled");
				pkg.setLabels(defaultsPlugin.settings.labels);
				if (defaultsPlugin.settings.description == null)
					throw new IllegalArgumentException("Must set description when shipkit is enabled");
				pkg.setDesc(defaultsPlugin.settings.description);
				pkg.setGithubRepo(githubRepo);
				pkg.setIssueTrackerUrl("https://github.com/" + githubRepo + "/issues");
			});

			if (defaultsPlugin.settings.downstreamRepositories.size() != 0) {
				project.getPlugins().apply(CiUpgradeDownstreamPlugin.class);
				project.getExtensions().getByType(UpgradeDownstreamExtension.class).setRepositories(defaultsPlugin.settings.downstreamRepositories);
			}

			if (defaultsPlugin.isTaskRequested(UpgradeDependencyPlugin.PERFORM_VERSION_UPGRADE)) {
				project.getPlugins().apply(UpgradeDependencyPlugin.class);
			}
		} else if (defaultsPlugin.settings.shipkit && project.getRootProject() == project && project.getVersion().equals("unspecified")) {
			val vi = Version.versionInfo(project.file("version.properties"), false);
			final String version = vi.getVersion() + "-SNAPSHOT";
			project.allprojects(project1 -> project1.setVersion(version));
		}
	}
}
