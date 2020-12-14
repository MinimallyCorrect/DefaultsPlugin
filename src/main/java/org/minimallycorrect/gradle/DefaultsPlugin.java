package org.minimallycorrect.gradle;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileTree;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.CoreJavadocOptions;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.tasks.JacocoReport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.diffplug.gradle.spotless.JavaExtension;
import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.gradle.spotless.SpotlessPlugin;

public class DefaultsPlugin implements Plugin<Project> {
	static final Charset CHARSET = StandardCharsets.UTF_8;
	protected static final String RELEASE_NOTES_PATH = "docs/release-notes.md";
	private static final String[] GENERATED_PATHS = {RELEASE_NOTES_PATH};
	DefaultsPluginExtension settings;

	private static String packageIfExists(@Nullable String in) {
		return in == null || in.isEmpty() ? "." + in : "";
	}

	@Override
	public void apply(@NotNull Project project) {
		project.getExtensions().add("minimallyCorrectDefaults", settings = new DefaultsPluginExtension(project));
		project.afterEvaluate(it -> {
			if (it.getState().getFailure() != null)
				return;

			if (!settings.hasRan) {
				throw new IllegalStateException("The minimallyCorrectDefaults extension should be called before project evaluation");
			}
		});
	}

	static void configure(final DefaultsPluginExtension settings, Project project) {
		if (project.getState().getFailure() != null)
			return;

		if (settings.languageLevel != null) {
			var ext = project.getExtensions().getByType(JavaPluginExtension.class);
			ext.setSourceCompatibility(settings.languageLevel);
			ext.setTargetCompatibility(settings.languageLevel);
		}

		project.getTasks().withType(Jar.class).all(jar -> {
			jar.setDuplicatesStrategy(DuplicatesStrategy.WARN);

			var attributes = jar.getManifest().getAttributes();

			attributes.put("Created-By", System.getProperty("java.vm.version") + " (" + System.getProperty("java.vm.vendor") + ")");
			attributes.put("Gradle-Version", project.getGradle().getGradleVersion());
			attributes.put("Group", project.getGroup());
			attributes.put("Name", project.getName());
			attributes.put("Implementation-Title", project.getGroup() + "." + project.getName() + packageIfExists(jar.getArchiveClassifier().getOrNull()));
		});

		project.getTasks().withType(JavaCompile.class).all(it -> {
			var options = it.getOptions();
			options.setEncoding("UTF-8");

			if (settings.javaWarnings) {
				options.setDeprecation(true);
				options.getCompilerArgs().addAll(Arrays.asList("-Xlint:all", "-Xlint:-path", "-Xlint:-processing", "-Xlint:-serial"));
				if (settings.ignoreSunInternalWarnings) {
					options.getCompilerArgs().add("-XDignore.symbol.file");
					options.setFork(true);
					options.getForkOptions().setExecutable("javac");
				}
				if (settings.treatWarningsAsErrors)
					options.getCompilerArgs().add("-Werror");
			}
		});

		project.getRepositories().jcenter();
		// TODO: deprecate this, we use jcenter now
		project.getRepositories().maven(it -> {
			it.setName("repo.nallar.me maven");
			it.setUrl("https://repo.nallar.me/");
			try {
				it.content(c -> {
					c.includeGroupByRegex("me\\.nallar.*");
					c.includeGroupByRegex("org\\.minimallycorrect.*");
				});
			} catch (NoSuchMethodError ignored) {
				// gradle < 5.1
			}
		});

		var javaPluginConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
		var sourceSets = javaPluginConvention.getSourceSets();

		if (settings.bintray) {
			BintrayExtensions.applyBintray(settings, project);
		}

		if (settings.noDocLint) {
			project.getTasks().withType(Javadoc.class).all(it -> {
				var options = it.getOptions();
				if (options instanceof CoreJavadocOptions) {
					((CoreJavadocOptions) options).addStringOption("Xdoclint:none", "-quiet");
				}
			});
		}

		if (settings.artifacts) {
			addArtifact(project, "sources", sourceSets.getByName("main").getAllSource());
			addArtifact(project, "javadoc", ((Javadoc) project.getTasks().getByName("javadoc")).getOutputs());
		}

		if (settings.spotless) {
			project.getPlugins().apply(SpotlessPlugin.class);
			var spotless = project.getExtensions().getByType(SpotlessExtension.class);
			if (settings.googleJavaFormat) {
				spotless.java(JavaExtension::googleJavaFormat);
			} else {
				File formatFile = getSpotlessFormatFile(project);
				if (!formatFile.exists()) {
					createSpotlessFormatFile(formatFile);
				}
				spotless.java(it -> {
					it.eclipse().configFile(formatFile);
					it.removeUnusedImports();
					it.importOrder("java", "javax", "lombok", "sun", "org", "com", "org.minimallycorrect", "");
					it.endWithNewline();
				});
			}
			if (settings.freshmark && project.getRootProject() == project) {
				spotless.freshmark(it -> {
					it.properties(props -> props.putAll(settings.toProperties(project)));
					it.target(files(project, "**/*.md"));
					it.indentWithTabs();
					it.endWithNewline();
				});
			}
			if (settings.ktLint) {
				boolean[] appliedKotlin = new boolean[]{false};
				project.getPlugins().all(it -> {
					if (it.getClass().getCanonicalName().startsWith("org.jetbrains.kotlin")) {
						if (!appliedKotlin[0]) {
							appliedKotlin[0] = true;
							var map = new HashMap<String, String>();
							map.put("indent_style", "tab");
							map.put("indent_size", "unset");
							spotless.kotlin(kotlin -> kotlin.ktlint().userData(map));
						}
					}
				});
			}
			spotless.format("misc", it -> {
				it.target(files(project, "/.gitignore", "/.gitattributes", "**/*.sh"));
				it.indentWithTabs();
				it.trimTrailingWhitespace();
				it.endWithNewline();
			});
		}

		if (settings.jacoco && (isCi() || isTaskRequested(project, "jacocoTestReport"))) {
			project.getPlugins().apply(JacocoPlugin.class);
			for (JacocoReport reportTask : project.getTasks().withType(JacocoReport.class)) {
				reportTask.getReports().forEach(it -> it.setEnabled(true));
				project.getTasks().getByName("check").dependsOn(reportTask);
			}
		}
	}

	private static void createSpotlessFormatFile(File formatFile) {
		//noinspection ResultOfMethodCallIgnored
		formatFile.getParentFile().mkdirs();
		var resource = DefaultsPlugin.class.getResource("/spotless/eclipse-config.xml");
		try {
			if (!formatFile.exists() || resource.openConnection().getContentLength() != formatFile.length())
				try (var is = resource.openStream()) {
					var parent = formatFile.getParentFile();
					if (!parent.isDirectory() && !parent.mkdirs())
						throw new IOError(new IOException("Failed to create " + parent));
					Files.copy(is, formatFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
		} catch (IOException e) {
			throw new IOError(e);
		}
		if (!formatFile.exists())
			throw new IOError(new IOException("Failed to create " + formatFile));
	}

	static String getGithubRepo(DefaultsPluginExtension settings) {
		String vcsUrl = getVcsUrl(settings);
		int lastIndexOfSlash = vcsUrl.lastIndexOf('/');
		int secondLast = vcsUrl.lastIndexOf('/', lastIndexOfSlash - 1);
		if (secondLast == -1)
			secondLast = vcsUrl.lastIndexOf(':', lastIndexOfSlash - 1);
		return vcsUrl.substring(secondLast + 1, vcsUrl.lastIndexOf('.'));
	}

	static String getVcsUrl(DefaultsPluginExtension settings) {
		return "git@github.com:" + settings.organisation + '/' + settings.repository + ".git";
	}

	@NotNull
	private static File getSpotlessFormatFile(Project project) {
		return new File(project.getBuildDir(), "spotless/eclipse-config.xml");
	}

	private static FileTree files(Project project, String... globs) {
		var args = new HashMap<String, Object>();
		args.put("dir", project.getRootDir());
		args.put("includes", Arrays.asList(globs));
		args.put("excludes", Arrays.asList(GENERATED_PATHS));
		return project.fileTree(args);
	}

	static String getWebsiteUrl(DefaultsPluginExtension settings) {
		if (settings.websiteUrl != null)
			return settings.websiteUrl;
		return (settings.websiteUrl = "https://github.com/" + getGithubRepo(settings));
	}

	private static void addArtifact(Project project, String name, Object... files) {
		if (project.getTasks().findByName(name + "Jar") != null)
			return;

		var task = project.getTasks().create(name + "Jar", Jar.class);
		task.getArchiveClassifier().set(name);
		task.from(files);
		project.getArtifacts().add("archives", task);
	}

	private static boolean isCi() {
		return Objects.equals(System.getenv("CI"), "true");
	}

	static boolean isTaskRequested(Project project, String taskName) {
		return project.getGradle().getStartParameter().getTaskNames().equals(Collections.singletonList(taskName));
	}

}
