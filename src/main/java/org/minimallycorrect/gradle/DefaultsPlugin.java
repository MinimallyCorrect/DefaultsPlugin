package org.minimallycorrect.gradle;

import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.gradle.spotless.SpotlessPlugin;
import com.jfrog.bintray.gradle.BintrayExtension;
import com.jfrog.bintray.gradle.BintrayPlugin;
import com.matthewprenger.cursegradle.CurseExtension;
import com.matthewprenger.cursegradle.CurseGradlePlugin;
import com.matthewprenger.cursegradle.CurseProject;
import com.matthewprenger.cursegradle.CurseUploadTask;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.val;
import net.minecraftforge.gradle.user.UserBaseExtension;
import net.minecraftforge.gradle.user.patcherUser.forge.ForgePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileTree;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.wrapper.Wrapper;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.tasks.JacocoReport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.shipkit.gradle.configuration.ShipkitConfiguration;
import org.shipkit.internal.gradle.java.ShipkitJavaPlugin;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class DefaultsPlugin implements Plugin<Project> {
	private static final String RELEASE_NOTES_PATH = "docs/release-notes.md";
	private static final String[] GENERATED_PATHS = {RELEASE_NOTES_PATH};
	private final Extension settings = new Extension();
	private Project project;
	private boolean initialised;
	private static final Charset CHARSET = Charset.forName("UTF-8");

	private static String packageIfExists(String in) {
		return in == null || in.isEmpty() ? "." + in : "";
	}

	@SneakyThrows
	private static void replace(File file, String from, String to) {
		Files.write(file.toPath(), new String(Files.readAllBytes(file.toPath()), CHARSET).replace(from, to).getBytes(CHARSET));
	}

	@SneakyThrows
	@Override
	public void apply(@NotNull Project project) {
		project.getExtensions().add("minimallyCorrectDefaults", settings);
		this.project = project;
		project.afterEvaluate(this::afterEvaluate);
	}

	private void afterEvaluate(Project project) {
		if (project.getState().getFailure() != null)
			return;

		if (!initialised)
			throw new RuntimeException("Should have called `minimallyCorrectDefaults()`");

		for (Jar jar : project.getTasks().withType(Jar.class)) {
			jar.setDuplicatesStrategy(DuplicatesStrategy.WARN);

			val attributes = jar.getManifest().getAttributes();

			if (settings.fmlCorePlugin != null)
				attributes.put("FMLCorePlugin", settings.fmlCorePlugin);
			if (settings.fmlCorePluginContainsFmlMod)
				attributes.put("FMLCorePluginContainsFMLMod", "true");
			attributes.put("Created-By", System.getProperty("java.vm.version") + " (" + System.getProperty("java.vm.vendor") + ")");
			attributes.put("Gradle-Version", project.getGradle().getGradleVersion());
			attributes.put("Group", project.getGroup());
			attributes.put("Name", project.getName());
			attributes.put("Implementation-Title", project.getGroup() + "." + project.getName() + packageIfExists(jar.getClassifier()));

			if (settings.minecraft != null)
				attributes.put("Minecraft-Version", settings.minecraft);
		}

		for (JavaCompile it : project.getTasks().withType(JavaCompile.class)) {
			if (settings.languageLevel != null) {
				it.setSourceCompatibility(settings.languageLevel);
				it.setTargetCompatibility(settings.languageLevel);
			}

			if (settings.javaWarnings) {
				val options = it.getOptions();
				options.setDeprecation(true);
				options.setEncoding("UTF-8");
				options.getCompilerArgs().addAll(Arrays.asList("-Xlint:all", "-Xlint:-path", "-Xlint:-processing", "-Xlint:-serial", "-XDignore.symbol.file"));
			}
		}
	}

	@Nullable
	private String getGithubRepo() {
		String vcsUrl = getVcsUrl();
		if (vcsUrl == null)
			return null;
		int lastIndexOfSlash = vcsUrl.lastIndexOf('/');
		int secondLast = vcsUrl.lastIndexOf('/', lastIndexOfSlash - 1);
		if (secondLast == -1)
			secondLast = vcsUrl.lastIndexOf(':', lastIndexOfSlash - 1);
		return vcsUrl.substring(secondLast + 1, vcsUrl.lastIndexOf('.'));
	}

	@SneakyThrows
	private String getVcsUrl() {
		if (settings.vcsUrl != null)
			return settings.vcsUrl;
		val out = new ByteArrayOutputStream();
		val result = project.exec(it -> {
			it.setCommandLine("git", "ls-remote", "--get-url", "origin");
			it.setStandardOutput(out);
		});
		if (result.getExitValue() != 0)
			throw new Error("Failed to get VCS URL");
		return (settings.vcsUrl = out.toString(CHARSET.name()));
	}

	@SneakyThrows
	private void configure() {
		initialised = true;
		Project project = this.project;

		project.getRepositories().jcenter();
		project.getRepositories().maven(it -> it.setUrl("https://repo.nallar.me/"));
		val javaPluginConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
		val sourceSets = javaPluginConvention.getSourceSets();

		if (settings.shipkit) {
			val shipkitGradle = project.file("gradle/shipkit.gradle");
			if (!shipkitGradle.exists()) {
				if (!shipkitGradle.getParentFile().isDirectory() && !shipkitGradle.getParentFile().mkdirs())
					throw new IOException("Failed to create directory for " + shipkitGradle);
				try (val is = getClass().getResourceAsStream("/shipkit/shipkit.gradle")) {
					Files.copy(is, shipkitGradle.toPath());
				}
			}
			val githubRepo = getGithubRepo();
			project.getExtensions().add("minimallyCorrectDefaultsShipkit", (Callable<Void>) () -> {
				val configuration = project.getExtensions().getByType(ShipkitConfiguration.class);
				configuration.getGitHub().setRepository(githubRepo);
				configuration.getGitHub().setReadOnlyAuthToken("bf61e48ac43dbad4d4a63ff664f5f9446adaa9c5");

				if (settings.minecraft != null) {
					configuration.getGit().setTagPrefix('v' + settings.minecraft + '_');
					configuration.getGit().setReleasableBranchRegex('^' + Pattern.quote(settings.minecraft) + "(/|$)");
				}

				return null;
			});
			project.getPlugins().apply(ShipkitJavaPlugin.class);
			project.getPlugins().apply(BintrayPlugin.class);

			val bintray = project.getExtensions().getByType(BintrayExtension.class);
			bintray.setUser("nallar");
			bintray.setKey(System.getenv("BINTRAY_KEY"));
			val pkg = bintray.getPkg();
			pkg.setName(project.getName());
			pkg.setRepo("minimallycorrectmaven");
			pkg.setUserOrg("minimallycorrect");
			pkg.setVcsUrl(getVcsUrl());
			pkg.setGithubReleaseNotesFile(RELEASE_NOTES_PATH);
			val website = getWebsiteUrl(githubRepo);
			if (website != null)
				pkg.setWebsiteUrl(website);
			if (settings.licenses == null)
				throw new IllegalArgumentException("Must set settings.licenses when shipkit is enabled");
			pkg.setLicenses(settings.licenses);
			if (settings.labels == null)
				throw new IllegalArgumentException("Must set labels when shipkit is enabled");
			pkg.setLabels(settings.labels);
			if (settings.description == null)
				throw new IllegalArgumentException("Must set description when shipkit is enabled");
			pkg.setDesc(settings.description);
			if (githubRepo != null) {
				pkg.setGithubRepo(githubRepo);
				pkg.setIssueTrackerUrl("https://github.com/" + githubRepo + "/issues");
			}

			if (settings.minecraft != null)
				project.setVersion(settings.minecraft + '-' + project.getVersion().toString());
		}

		if (settings.artifacts) {
			addArtifact("sources", sourceSets.getByName("main").getAllSource());
			addArtifact("javadoc", ((Javadoc) project.getTasks().getByName("javadoc")).getOutputs());
			if (settings.minecraft != null)
				addArtifact("deobf", sourceSets.getByName("main").getOutput());
		}

		if (settings.spotBugs) {
			/* TODO: use once https://github.com/gradle/gradle/pull/2538 is done */
			/*
			project.getPlugins().apply("com.github.spotbugs");
			//project.getDependencies().add("spotbugs", "com.github.spotbugs:spotbugs:3.1.0-RC5");
			val extension = (SpotBugsExtension) project.getExtensions().getByName("spotbugs");
			extension.setToolVersion("3.1.0-RC5");
			val javaPluginConvention = project.getConvention().findPlugin(JavaPluginConvention.class);
			extension.setSourceSets(Collections.singleton(javaPluginConvention.getSourceSets().getByName("main")));
			extension.setIgnoreFailures(false);
			extension.setReportsDir(project.file("build/findbugs"));
			extension.setEffort("max");
			extension.setReportLevel("low");
			StringBuilder fbExclude = new StringBuilder();
			fbExclude.append("<FindBugsFilter><Match>");
			for (String findBugsExcludeBug : settings.spotBugsExclusions)
				fbExclude.append("<Not><Bug pattern=\"").append(findBugsExcludeBug).append("\"/></Not>");
			fbExclude.append("</Match></FindBugsFilter>");
			extension.setIncludeFilterConfig(project.getResources().getText().fromString(fbExclude.toString()));
			project.getTasks().withType(FindBugs.class, it -> {
				it.getReports().forEach(report -> report.setEnabled(false));
				it.getReports().getHtml().setEnabled(true);
			});
			*/
		}

		if (settings.spotless) {
			project.getPlugins().apply(SpotlessPlugin.class);
			val spotless = project.getExtensions().getByType(SpotlessExtension.class);
			//spotless.java(JavaExtension::googleJavaFormat);
			spotless.java(it -> {
				File formatFile = new File(project.getBuildDir(), "spotless/eclipse-config.xml");
				val resource = this.getClass().getResource("/spotless/eclipse-config.xml");
				try {
					if (resource.openConnection().getContentLength() != formatFile.length())
						try (val is = resource.openStream()) {
							val parent = formatFile.getParentFile();
							if (!parent.isDirectory() && !parent.mkdirs())
								throw new IOError(new IOException("Failed to create " + parent));
							Files.copy(is, formatFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
						}
				} catch (IOException e) {
					throw new IOError(e);
				}
				it.eclipse().configFile(formatFile);
				it.custom("Lambda fix", s -> s.replace("} )", "})").replace("} ,", "},"));
				it.custom("noinspection fix", s -> s.replace("// noinspection", "//noinspection"));
			});
			spotless.freshmark(it -> {
				it.target(files("**/*.md"));
				it.indentWithTabs();
				it.trimTrailingWhitespace();
				it.endWithNewline();
			});
			spotless.format("misc", it -> {
				it.target(files("/.gitignore", "/.gitattributes", "**/*.sh"));
				it.indentWithTabs();
				it.trimTrailingWhitespace();
				it.endWithNewline();
			});
		}

		if (settings.jacoco) {
			project.getPlugins().apply(JacocoPlugin.class);
			for (JacocoReport reportTask : project.getTasks().withType(JacocoReport.class)) {
				reportTask.getReports().forEach(it -> it.setEnabled(true));
				project.getTasks().getByName("check").dependsOn(reportTask);
			}
		}

		for (String target : settings.dependencyTargets) {
			settings.annotationDependencyCoordinates.forEach((it) -> project.getDependencies().add(target, it));
			settings.lombokDependencyCoordinates.forEach((it) -> project.getDependencies().add(target, it));
		}

		if (settings.minecraft != null) {
			project.getPlugins().apply(ForgePlugin.class);
			configureMinecraft(project.getExtensions().getByType(UserBaseExtension.class));
			if (settings.wrapperJavaArgs == null)
				settings.wrapperJavaArgs = "-Xmx2G";

			val map = new HashMap<String, String>();
			map.put("version", project.getVersion().toString());
			map.put("mcversion", settings.minecraft);

			val resources = project.getTasks().maybeCreate("procesResources", ProcessResources.class);
			resources.getInputs().properties(map);
			resources.filesMatching("mcmod.info", it -> it.expand(map));

			val apiKey = System.getenv("CURSEFORGE_API_KEY");
			if (settings.curseforgeProject != null && apiKey != null) {
				project.getPlugins().apply(CurseGradlePlugin.class);
				val extension = project.getExtensions().getByType(CurseExtension.class);
				extension.setApiKey(apiKey);
				val curseProject = new CurseProject();
				curseProject.setId(settings.curseforgeProject);
				curseProject.setApiKey(apiKey);
				curseProject.setChangelog(new FileReader(project));
				curseProject.setReleaseType("beta");
				maybeAddArtifact("sourceJar", curseProject);
				maybeAddArtifact("deobfJar", curseProject);
				maybeAddArtifact("javadocJar", curseProject);
				extension.getCurseProjects().add(curseProject);

				if (settings.shipkit) {
					project.getTasks().withType(CurseUploadTask.class).forEach(it -> it.dependsOn(project.getTasks().getByName("updateReleaseNotes")));
					project.getTasks().getByName("performRelease").dependsOn(project.getTasks().getByName("curseforge"));
				}
			}
		}

		if (settings.wrapperJavaArgs != null) {
			val wrapper = project.getTasks().maybeCreate("wrapper", Wrapper.class);
			wrapper.getInputs().property("javaArgs", settings.wrapperJavaArgs);
			wrapper.doLast(ignored -> {
				val optsEnvVar = "DEFAULT_JVM_OPTS";
				replace(wrapper.getScriptFile(), optsEnvVar + "=\"\"", optsEnvVar + "=\"" + settings.wrapperJavaArgs + "\"");
				replace(wrapper.getBatchScript(), "set " + optsEnvVar + "=", "set " + optsEnvVar + "=" + settings.wrapperJavaArgs);
			});
		}
	}

	private FileTree files(String... globs) {
		val args = new HashMap<String, Object>();
		args.put("dir", project.getRootDir());
		args.put("includes", Arrays.asList(globs));
		args.put("excludes", Arrays.asList(GENERATED_PATHS));
		return project.fileTree(args);
	}

	private void maybeAddArtifact(String name, CurseProject curseProject) {
		val curseforge = project.getTasks().findByName("curseforge");
		val task = project.getTasks().findByName(name);
		if (curseforge == null || task == null)
			return;
		curseforge.dependsOn(task);
		curseProject.addArtifact(task);
	}

	@Nullable
	private String getWebsiteUrl(@Nullable String githubRepo) {
		if (settings.websiteUrl != null)
			return settings.websiteUrl;
		if (githubRepo != null)
			return (settings.websiteUrl = "https://github.com/" + githubRepo);
		return null;
	}

	private void addArtifact(String name, Object... files) {
		if (project.getTasks().findByName(name + "Jar") != null)
			return;

		val task = project.getTasks().create(name + "Jar", Jar.class);
		task.setClassifier(name);
		task.from(files);
		project.getArtifacts().add("archives", task);
	}

	private void configureMinecraft(UserBaseExtension minecraft) {
		String mcVersion = settings.minecraft;
		minecraft.setVersion(mcVersion + '-' + getForge(mcVersion));
		minecraft.setMappings(getMappings(mcVersion));
		minecraft.setRunDir("run");
		minecraft.replace("@MOD_VERSION@", project.getVersion().toString());
		minecraft.replace("@MC_VERSION@", mcVersion);
	}

	private String getMappings(String minecraft) {
		if (settings.minecraftMappings != null)
			return settings.minecraftMappings;

		switch (minecraft) {
			case "1.12.1":
				return "snapshot_20170624";
			case "1.12":
				return "snapshot_20170617";
			case "1.11.2":
				return "snapshot_20161220";
			case "1.10.2":
				return "snapshot_20160518";
		}

		throw new IllegalArgumentException("Unsupported minecraft version " + minecraft);
	}

	private String getForge(String minecraft) {
		if (settings.forge != null)
			return settings.forge;

		switch (minecraft) {
			case "1.12.1":
				return "14.22.1.2484";
			case "1.12":
				return "14.21.0.2340";
			case "1.11.2":
				return "13.20.0.2216";
			case "1.10.2":
				return "12.18.1.2076";
		}

		throw new IllegalArgumentException("Unsupported minecraft version " + minecraft);
	}

	private static class FileReader {
		private final Project project;
		String cached;

		FileReader(Project project) {
			this.project = project;
		}

		@Override
		public String toString() {
			String cached = this.cached;
			if (cached != null)
				return cached;
			try {
				cached = com.google.common.io.Files.toString(project.file(RELEASE_NOTES_PATH), CHARSET);
			} catch (IOException e) {
				cached = "Failed to read changelog from " + project.file(RELEASE_NOTES_PATH);
				project.getLogger().error(cached, e);
			}
			return (this.cached = cached);
		}
	}

	@Data
	public class Extension implements Callable<Void> {
		public final List<String> repos = new ArrayList<>(Arrays.asList(
			"https://repo.nallar.me/"
		));
		public final List<String> dependencyTargets = new ArrayList<>(Arrays.asList("compileOnly", "testCompileOnly"));
		public final List<String> annotationDependencyCoordinates = new ArrayList<>(Arrays.asList(
			"com.google.code.findbugs:jsr305:3.0.2",
			"net.jcip:jcip-annotations:1.0",
			"org.jetbrains:annotations:15.0"
		));
		public final List<String> lombokDependencyCoordinates = new ArrayList<>(Arrays.asList(
			"org.projectlombok:lombok:1.16.16"
		));
		public String languageLevel = "8";
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
			"RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE"
		);
		public boolean spotless = true;
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

		@Override
		public Void call() {
			DefaultsPlugin.this.configure();
			return null;
		}
	}
}
