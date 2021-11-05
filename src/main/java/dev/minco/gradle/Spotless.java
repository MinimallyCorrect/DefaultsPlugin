package dev.minco.gradle;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import org.gradle.api.Project;
import org.gradle.api.file.FileTree;
import org.jetbrains.annotations.NotNull;

import com.diffplug.gradle.spotless.JavaExtension;
import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.gradle.spotless.SpotlessPlugin;

public class Spotless {
	static void applySpotlessSettings(DefaultsPluginExtension settings, Project project) {
		project.getPlugins().apply(SpotlessPlugin.class);
		var spotless = project.getExtensions().getByType(SpotlessExtension.class);
		if (settings.googleJavaFormat) {
			spotless.java(JavaExtension::googleJavaFormat);
		} else {
			File formatFile = setUpSpotlessFormatFile(project.getBuildDir());
			File buildDir = project.getBuildDir();
			project.getTasks().matching(it -> it.getName().equals("clean")).configureEach(cleanTask -> cleanTask.doLast(task -> Spotless.setUpSpotlessFormatFile(buildDir)));
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
				it.target("**/*.md");
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
			it.target(".gitignore", ".gitattributes", "**/*.sh");
			it.indentWithTabs();
			it.trimTrailingWhitespace();
			it.endWithNewline();
		});
	}

	@NotNull
	static File setUpSpotlessFormatFile(File buildDir) {
		File formatFile = getSpotlessFormatFile(buildDir);
		if (!formatFile.exists()) {
			createSpotlessFormatFile(formatFile);
		}
		return formatFile;
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

	@NotNull
	private static File getSpotlessFormatFile(File buildDir) {
		return new File(buildDir, "spotless/eclipse-config.xml");
	}

	private static FileTree files(Project project, String... globs) {
		var args = new HashMap<String, Object>();
		args.put("dir", project.getRootDir());
		args.put("includes", Arrays.asList(globs));
		args.put("excludes", Collections.emptyList());
		return project.fileTree(args);
	}
}
