package org.minimallycorrect.gradle;

import java.io.IOError;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

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
	static void initShipkit(DefaultsPluginExtension settings, Project project) {
		var shouldApplyShipKit = DefaultsPlugin.shouldApplyShipKit(settings, project);
		if (shouldApplyShipKit) {
			var shipkitGradle = project.file("gradle/shipkit.gradle");
			if (!shipkitGradle.exists()) {
				if (!shipkitGradle.getParentFile().isDirectory() && !shipkitGradle.getParentFile().mkdirs())
					throw new IOError(new IOException("Failed to create directory for " + shipkitGradle));
				try {
					try (var is = ShipkitExtensions.class.getResourceAsStream("/shipkit/shipkit.gradle")) {
						Files.copy(is, shipkitGradle.toPath());
					}
				} catch (IOException e) {
					throw new IOError(e);
				}
			}
			var githubRepo = DefaultsPlugin.getGithubRepo(settings);
			project.getExtensions().add("minimallyCorrectDefaultsShipkit", (Callable<Void>) () -> {
				var configuration = project.getExtensions().getByType(ShipkitConfiguration.class);
				configuration.getGitHub().setRepository(githubRepo);
				configuration.getGitHub().setReadOnlyAuthToken("bf61e48ac43dbad4d4a63ff664f5f9446adaa9c5");
				configuration.getGit().setCommitMessagePostfix("[ci skip-release]");
				configuration.getReleaseNotes().setIgnoreCommitsContaining(Arrays.asList("[ci skip]", "[ci skip-release]"));

				if (settings.minecraft != null) {
					configuration.getGit().setTagPrefix('v' + settings.minecraft + '_');
					configuration.getGit().setReleasableBranchRegex('^' + Pattern.quote(settings.minecraft) + "(/|$)");
				}

				return null;
			});
			project.getPlugins().apply(ShipkitJavaPlugin.class);

			if (settings.minecraft != null)
				project.setVersion(settings.minecraft + '-' + project.getVersion().toString());

			project.allprojects(it -> {
				it.getPlugins().apply(BintrayPlugin.class);
				var bintray = it.getExtensions().getByType(BintrayExtension.class);
				bintray.setUser("nallar");
				bintray.setKey(System.getenv("BINTRAY_KEY"));
				var pkg = bintray.getPkg();
				pkg.setName(project.getName());
				pkg.setRepo("minimallycorrectmaven");
				pkg.setUserOrg("minimallycorrect");
				pkg.setVcsUrl(DefaultsPlugin.getVcsUrl(settings));
				pkg.setGithubReleaseNotesFile(DefaultsPlugin.RELEASE_NOTES_PATH);
				pkg.setWebsiteUrl(DefaultsPlugin.getWebsiteUrl(settings));
				if (settings.licenses == null)
					throw new IllegalArgumentException("Must set settings.licenses when shipkit is enabled");
				pkg.setLicenses(settings.licenses);
				if (settings.labels == null)
					throw new IllegalArgumentException("Must set labels when shipkit is enabled");
				pkg.setLabels(settings.labels);
				if (settings.description == null)
					throw new IllegalArgumentException("Must set description when shipkit is enabled");
				pkg.setDesc(settings.description);
				pkg.setGithubRepo(githubRepo);
				pkg.setIssueTrackerUrl("https://github.com/" + githubRepo + "/issues");
			});

			if (settings.downstreamRepositories.size() != 0) {
				project.getPlugins().apply(CiUpgradeDownstreamPlugin.class);
				project.getExtensions().getByType(UpgradeDownstreamExtension.class).setRepositories(settings.downstreamRepositories);
			}

			if (DefaultsPlugin.isTaskRequested(project, UpgradeDependencyPlugin.PERFORM_VERSION_UPGRADE)) {
				project.getPlugins().apply(UpgradeDependencyPlugin.class);
			}
		} else if (settings.shipkit && project.getRootProject() == project && project.getVersion().equals("unspecified")) {
			var vi = Version.versionInfo(project.file("version.properties"), false);
			final String version = vi.getVersion() + "-SNAPSHOT";
			project.allprojects(project1 -> project1.setVersion(version));
		}
	}
}
