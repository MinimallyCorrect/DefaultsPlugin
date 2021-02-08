package org.minimallycorrect.gradle;

import java.util.*;

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;

public class DefaultsPluginExtension {
	public JavaVersion languageLevel = JavaVersion.VERSION_11;
	public boolean javaWarnings = true;
	public boolean spotless = true;
	public boolean googleJavaFormat = false;
	public boolean jacoco = true;
	public boolean artifacts = true;
	public String websiteUrl = null;
	public String[] labels;
	public String[] licenses = {"MIT"};
	public String description;
	public String organisation = "MinimallyCorrect";
	public String repository;
	public boolean freshmark;
	public boolean ktLint = true;
	public boolean ignoreSunInternalWarnings = false;
	public boolean treatWarningsAsErrors = true;
	public boolean noDocLint = true;
	boolean hasRan = false;

	DefaultsPluginExtension(Project project) {
		repository = project.getRootProject().getName();
		freshmark = project.hasProperty("applyFreshmark") || DefaultsPlugin.isTaskRequested(project, "performRelease");
	}

	Map<String, Object> toProperties(Project project) {
		var props = new HashMap<String, Object>();
		props.put("organisation", organisation);
		props.put("name", project.getName());
		props.put("group", project.getGroup());
		props.put("version", project.getVersion());
		if (licenses.length == 1)
			props.put("license", licenses[0]);
		props.put("licenses", Arrays.toString(licenses));
		props.put("releaseNotesPath", DefaultsPlugin.RELEASE_NOTES_PATH);
		props.put("branch", Git.getBranch(project));
		props.put("discordId", "313371711632441344");
		props.put("discordInvite", "https://discord.gg/YrV3bDm");
		return props;
	}

	@SuppressWarnings("unused")
	public void configureProject(Project project) {
		hasRan = true;
		DefaultsPlugin.configure(this, project);
	}
}
