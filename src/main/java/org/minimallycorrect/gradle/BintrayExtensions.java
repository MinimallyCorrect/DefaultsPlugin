package org.minimallycorrect.gradle;

import org.gradle.api.Project;

import com.jfrog.bintray.gradle.BintrayExtension;
import com.jfrog.bintray.gradle.BintrayPlugin;

public class BintrayExtensions {
	public static void applyBintray(DefaultsPluginExtension settings, Project project) {
		project.allprojects(it -> {
			it.getPlugins().apply(BintrayPlugin.class);
			it.getExtensions().configure(BintrayExtension.class, (bintray) -> {
				bintray.setPublish(true);
				bintray.setUser(System.getenv("BINTRAY_USER"));
				bintray.setKey(System.getenv("BINTRAY_KEY"));
				var pkg = bintray.getPkg();
				pkg.setName(project.getName());
				pkg.setRepo("minimallycorrectmaven");
				pkg.setUserOrg("minimallycorrect");
				pkg.setVcsUrl(DefaultsPlugin.getVcsUrl(settings));
				pkg.setGithubReleaseNotesFile(DefaultsPlugin.RELEASE_NOTES_PATH);
				pkg.setWebsiteUrl(DefaultsPlugin.getWebsiteUrl(settings));
				if (settings.licenses == null)
					throw new IllegalArgumentException("Must set settings.licenses when bintray is enabled");
				pkg.setLicenses(settings.licenses);
				if (settings.labels == null)
					throw new IllegalArgumentException("Must set labels when bintray is enabled");
				pkg.setLabels(settings.labels);
				if (settings.description == null)
					throw new IllegalArgumentException("Must set description when bintray is enabled");
				pkg.setDesc(settings.description);
				var githubRepo = DefaultsPlugin.getGithubRepo(settings);
				pkg.setGithubRepo(githubRepo);
				pkg.setIssueTrackerUrl("https://github.com/" + githubRepo + "/issues");
				pkg.getVersion().setName(project.getVersion().toString());
				pkg.getVersion().setVcsTag("v" + project.getVersion());
			});
		});
	}
}
