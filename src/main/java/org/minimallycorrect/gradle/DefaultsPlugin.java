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

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileTree;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.wrapper.Wrapper;
import org.gradle.external.javadoc.CoreJavadocOptions;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.tasks.JacocoReport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.shipkit.gradle.notes.UpdateReleaseNotesTask;
import org.shipkit.internal.gradle.versionupgrade.UpgradeDependencyPlugin;

import com.diffplug.gradle.spotless.JavaExtension;
import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.gradle.spotless.SpotlessPlugin;

import net.minecraftforge.gradle.user.UserBaseExtension;
import net.minecraftforge.gradle.user.patcherUser.forge.ForgePlugin;

// gradle warning: Using Java lambdas is not supported, use an (anonymous) inner class instead.
@SuppressWarnings("Convert2Lambda")
public class DefaultsPlugin implements Plugin<Project> {
	static final Charset CHARSET = StandardCharsets.UTF_8;
	protected static final String RELEASE_NOTES_PATH = "docs/release-notes.md";
	private static final String[] GENERATED_PATHS = {RELEASE_NOTES_PATH};
	DefaultsPluginExtension settings;

	private static String packageIfExists(@Nullable String in) {
		return in == null || in.isEmpty() ? "." + in : "";
	}

	private static void replace(File file, String from, String to) {
		try {
			Files.write(file.toPath(), new String(Files.readAllBytes(file.toPath()), CHARSET).replace(from, to).getBytes(CHARSET));
		} catch (IOException e) {
			throw new IOError(e);
		}
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

			if (settings.fmlCorePlugin != null)
				attributes.put("FMLCorePlugin", settings.fmlCorePlugin);
			if (settings.fmlCorePluginContainsFmlMod)
				attributes.put("FMLCorePluginContainsFMLMod", "true");
			attributes.put("Created-By", System.getProperty("java.vm.version") + " (" + System.getProperty("java.vm.vendor") + ")");
			attributes.put("Gradle-Version", project.getGradle().getGradleVersion());
			attributes.put("Group", project.getGroup());
			attributes.put("Name", project.getName());
			attributes.put("Implementation-Title", project.getGroup() + "." + project.getName() + packageIfExists(jar.getArchiveClassifier().getOrNull()));

			if (settings.minecraft != null)
				attributes.put("Minecraft-Version", settings.minecraft);
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

		ShipkitExtensions.initShipkit(settings, project);

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
			if (settings.minecraft != null)
				addArtifact(project, "deobf", sourceSets.getByName("main").getOutput());
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
				project.getTasks().withType(UpdateReleaseNotesTask.class).all(it -> it.dependsOn("spotlessFreshmarkApply"));
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

		if (settings.minecraft != null) {
			project.getPlugins().apply(ForgePlugin.class);
			ForgeExtensions.configureMinecraft(project, settings, project.getExtensions().getByType(UserBaseExtension.class));
			if (settings.wrapperJavaArgs == null)
				settings.wrapperJavaArgs = "-Xmx2G";

			var map = new HashMap<String, String>();
			map.put("version", project.getVersion().toString());
			map.put("mcversion", settings.minecraft);

			var resources = project.getTasks().maybeCreate("processResources", ProcessResources.class);
			resources.getInputs().properties(map);
			resources.filesMatching("mcmod.info", it -> it.expand(map));

			var apiKey = System.getenv("CURSEFORGE_API_KEY");
			if (settings.curseforgeProject != null && apiKey != null) {
				CurseExtensions.applyCursePlugin(settings, project, apiKey);
			}
		}

		if (settings.wrapperJavaArgs != null) {
			var wrapper = project.getTasks().maybeCreate("wrapper", Wrapper.class);
			wrapper.getInputs().property("javaArgs", settings.wrapperJavaArgs);
			wrapper.doLast(new Action<Task>() {
				@Override
				public void execute(Task ignored) {
					var optsEnvVar = "DEFAULT_JVM_OPTS";
					replace(wrapper.getScriptFile(), optsEnvVar + "=\"\"", optsEnvVar + "=\"" + settings.wrapperJavaArgs + "\"");
					replace(wrapper.getBatchScript(), "set " + optsEnvVar + "=", "set " + optsEnvVar + "=" + settings.wrapperJavaArgs);
				}
			});
		}
	}

	private static void createSpotlessFormatFile(File formatFile) {
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

	static boolean shouldApplyShipKit(DefaultsPluginExtension settings, Project project) {
		return settings.shipkit &&
			project.getRootProject() == project &&
			(project.hasProperty("applyShipkit") ||
				DefaultsPlugin.isTaskRequested(project, "testRelease") ||
				DefaultsPlugin.isTaskRequested(project, "releaseNeeded") ||
				DefaultsPlugin.isTaskRequested(project, "initShipkit") ||
				DefaultsPlugin.isTaskRequested(project, UpgradeDependencyPlugin.PERFORM_VERSION_UPGRADE) ||
				DefaultsPlugin.isCi());
	}

	private static boolean isCi() {
		return Objects.equals(System.getenv("TRAVIS"), "true") ||
			Objects.equals(System.getenv("CI"), "true");
	}

	static boolean isTaskRequested(Project project, String taskName) {
		return project.getGradle().getStartParameter().getTaskNames().equals(Collections.singletonList(taskName));
	}

}
