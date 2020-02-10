package org.minimallycorrect.gradle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.val;

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;

@Getter
@Setter
@ToString
public class DefaultsPluginExtension {
	public final List<String> annotationDependencyTargets = new ArrayList<>(Arrays.asList("compileOnly", "testCompileOnly"));
	public final List<String> annotationProcessorDependencyTargets = new ArrayList<>(Arrays.asList("compileOnly", "testCompileOnly", "annotationProcessor", "testAnnotationProcessor"));
	public final List<String> annotationDependencyCoordinates = new ArrayList<>(Collections.singletonList(
		"org.jetbrains:annotations:18.0.0"));
	public final List<String> lombokDependencyCoordinates = new ArrayList<>(Collections.singletonList(
		"org.projectlombok:lombok:1.18.10"));
	public final List<String> downstreamRepositories = new ArrayList<>();
	public JavaVersion languageLevel = JavaVersion.VERSION_1_8;
	public boolean javaWarnings = true;
	public String minecraft = null;
	public String minecraftMappings = null;
	public String forge = null;
	public String fmlCorePlugin = null;
	public boolean fmlCorePluginContainsFmlMod = false;
	public boolean spotBugs = true;
	public List<String> spotBugsExclusions = Arrays.asList(
		"DM_CONVERT_CASE",
		"SE_NO_SERIALVERSIONID",
		"MS_SHOULD_BE_FINAL",
		"MS_CANNOT_BE_FINAL",
		"RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE");
	public boolean spotless = true;
	public boolean googleJavaFormat = false;
	public boolean jacoco = true;
	public boolean shipkit = true;
	public boolean artifacts = true;
	public String wrapperJavaArgs = null;
	public String vcsUrl = null;
	public String websiteUrl = null;
	public String curseforgeProject = null;
	public String[] labels;
	public String[] licenses = {"MIT"};
	public String description;
	public String organisation = "MinimallyCorrect";
	public String bintrayRepo = (organisation + "/minimallycorrectmaven").toLowerCase();
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
		val props = new HashMap<String, Object>();
		props.put("organisation", organisation);
		props.put("bintrayrepo", bintrayRepo);
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

	String getForge(String minecraft) {
		if (forge != null)
			return forge;

		return ForgeExtensions.getForge(minecraft);
	}

	String getMappings(String minecraft) {
		if (minecraftMappings != null)
			return minecraftMappings;

		return ForgeExtensions.getMappings(minecraft);
	}

	@SuppressWarnings("unused")
	public void configureProject(Project project) {
		hasRan = true;
		DefaultsPlugin.configure(this, project);
	}
}
