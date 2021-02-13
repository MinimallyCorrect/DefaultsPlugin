package dev.minco.gradle;

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
	public boolean freshmark;
	public boolean ktLint = true;
	public boolean treatWarningsAsErrors = true;
	public boolean noDocLint = true;
	boolean hasRan = false;

	DefaultsPluginExtension(Project project) {
		freshmark = project.hasProperty("applyFreshmark") || DefaultsPlugin.isTaskRequested(project, "performRelease");
	}

	Map<String, Object> toProperties(Project project) {
		var props = new HashMap<String, Object>();
		props.put("organisation", project.property("githubOwner"));
		props.put("name", project.getName());
		props.put("group", project.getGroup());
		props.put("version", project.getVersion());
		props.put("licenses", project.property("licenses"));
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
