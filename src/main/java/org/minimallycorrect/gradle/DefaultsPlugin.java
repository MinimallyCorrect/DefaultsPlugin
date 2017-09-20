package org.minimallycorrect.gradle;

import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.gradle.spotless.SpotlessPlugin;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.val;
import net.minecraftforge.gradle.user.UserBaseExtension;
import net.minecraftforge.gradle.user.patcherUser.forge.ForgePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.wrapper.Wrapper;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.tasks.JacocoReport;
import org.shipkit.internal.gradle.configuration.ShipkitConfigurationPlugin;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class DefaultsPlugin implements Plugin<Project> {
	private final Extension settings = new Extension();
	private Project project;
	private boolean initialised;

	private static String packageIfExists(String in) {
		return in == null || in.isEmpty() ? "." + in : "";
	}

	@SneakyThrows
	private static void replace(File file, String from, String to) {
		Files.write(file.toPath(), new String(Files.readAllBytes(file.toPath()), Charsets.UTF_8).replace(from, to).getBytes(Charsets.UTF_8));
	}

	@SneakyThrows
	@Override
	public void apply(Project project) {
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

	private void configure() {
		initialised = true;
		Project project = this.project;

		project.getRepositories().jcenter();
		project.getRepositories().maven(it -> it.setUrl("https://repo.nallar.me/"));
		val javaPluginConvention = project.getConvention().findPlugin(JavaPluginConvention.class);
		val sourceSets = javaPluginConvention.getSourceSets();

		if (settings.artifacts) {
			addArtifact("sources", sourceSets.getByName("main").getAllSource());
			addArtifact("javadoc", ((Javadoc) project.getTasks().getByName("javadoc")).getOutputs());
			addArtifact("deobf", sourceSets.getByName("main").getOutput());
		}

		if (settings.shipkit) {
			project.getPlugins().apply(ShipkitConfigurationPlugin.class).getConfiguration();
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
			val spotless = project.getExtensions().findByType(SpotlessExtension.class);
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
					throw Throwables.propagate(e);
				}
				it.eclipse().configFile(formatFile);
				it.custom("Lambda fix", s -> s.replace("} )", "})").replace("} ,", "},"));
				it.custom("noinspection fix", s -> s.replace("// noinspection", "//noinspection"));
			});
			spotless.format("misc", it -> {
				it.target("/.gitignore", "/.gitattributes", "**/*.md", "**/*.sh");
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

	private void addArtifact(String name, Object... files) {
		val task = project.getTasks().maybeCreate(name + "Jar", Jar.class);
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

	@Data
	public class Extension implements Callable<Void> {
		public final List<String> repos = new ArrayList<>(Arrays.asList(
			"https://repo.nallar.me/"
		));
		public final List<String> dependencyTargets = new ArrayList<>(Arrays.asList("compileOnly", "testCompileOnly"));
		public final List<String> annotationDependencyCoordinates = new ArrayList<>(Arrays.asList(
			"com.google.code.findbugs:jsr305:3.0.2",
			"net.jcip:jcip-annotations:1.0"
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

		@Override
		public Void call() {
			DefaultsPlugin.this.configure();
			return null;
		}
	}
}
