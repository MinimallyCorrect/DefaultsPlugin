package dev.minco.gradle;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.DuplicatesStrategy;
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

public class DefaultsPlugin implements Plugin<Project> {
	static final Charset CHARSET = StandardCharsets.UTF_8;
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

		project.getTasks().withType(Jar.class).configureEach(jar -> {
			jar.setDuplicatesStrategy(DuplicatesStrategy.WARN);

			var attributes = jar.getManifest().getAttributes();

			attributes.put("Created-By", System.getProperty("java.vm.version") + " (" + System.getProperty("java.vm.vendor") + ")");
			attributes.put("Gradle-Version", project.getGradle().getGradleVersion());
			attributes.put("Group", project.getGroup());
			attributes.put("Name", project.getName());
			attributes.put("Implementation-Title", project.getGroup() + "." + project.getName() + packageIfExists(jar.getArchiveClassifier().getOrNull()));
		});

		project.getTasks().withType(JavaCompile.class).configureEach(it -> {
			var options = it.getOptions();
			options.setEncoding("UTF-8");

			if (settings.javaWarnings) {
				options.setDeprecation(true);
				options.getCompilerArgs().addAll(Arrays.asList("-Xlint:all", "-Xlint:-path", "-Xlint:-processing", "-Xlint:-serial"));
				if (settings.treatWarningsAsErrors)
					options.getCompilerArgs().add("-Werror");
			}
		});

		var javaPluginConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
		var sourceSets = javaPluginConvention.getSourceSets();

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
			Spotless.applySpotlessSettings(settings, project);
		}

		if (settings.jacoco && (isCi() || isTaskRequested(project, "jacocoTestReport"))) {
			project.getPlugins().apply(JacocoPlugin.class);
			for (JacocoReport reportTask : project.getTasks().withType(JacocoReport.class)) {
				reportTask.getReports().forEach(it -> it.setEnabled(true));
				project.getTasks().getByName("check").dependsOn(reportTask);
			}
		}
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
