package org.minimallycorrect.gradle;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.val;

import org.apache.groovy.util.Maps;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileTree;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.wrapper.Wrapper;
import org.gradle.external.javadoc.CoreJavadocOptions;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.tasks.JacocoReport;
import org.jetbrains.annotations.NotNull;
import org.shipkit.gradle.configuration.ShipkitConfiguration;
import org.shipkit.internal.gradle.java.ShipkitJavaPlugin;
import org.shipkit.internal.gradle.version.VersioningPlugin;
import org.shipkit.internal.gradle.versionupgrade.CiUpgradeDownstreamPlugin;
import org.shipkit.internal.gradle.versionupgrade.UpgradeDependencyPlugin;
import org.shipkit.internal.gradle.versionupgrade.UpgradeDownstreamExtension;

import com.diffplug.gradle.spotless.JavaExtension;
import com.diffplug.gradle.spotless.SpotlessExtension;
import com.diffplug.gradle.spotless.SpotlessPlugin;
import com.jfrog.bintray.gradle.BintrayExtension;
import com.jfrog.bintray.gradle.BintrayPlugin;

import net.minecraftforge.gradle.user.UserBaseExtension;
import net.minecraftforge.gradle.user.patcherUser.forge.ForgePlugin;

// gradle warning: Using Java lambdas is not supported, use an (anonymous) inner class instead.
@SuppressWarnings("Convert2Lambda")
public class DefaultsPlugin implements Plugin<Project> {
	static final Charset CHARSET = StandardCharsets.UTF_8;
	protected static final String RELEASE_NOTES_PATH = "docs/release-notes.md";
	private static final String[] GENERATED_PATHS = {RELEASE_NOTES_PATH};
	private Extension settings;
	private Project project;
	private boolean initialised;

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
		this.project = project;
		project.getExtensions().add("minimallyCorrectDefaults", settings = new Extension());
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
			attributes.put("Implementation-Title", project.getGroup() + "." + project.getName() + packageIfExists(jar.getArchiveClassifier().getOrNull()));

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
				options.getCompilerArgs().addAll(Arrays.asList("-Xlint:all", "-Xlint:-path", "-Xlint:-processing", "-Xlint:-serial"));
				if (settings.ignoreSunInternalWarnings) {
					options.getCompilerArgs().add("-XDignore.symbol.file");
					options.setFork(true);
					options.getForkOptions().setExecutable("javac");
				}
				if (settings.treatWarningsAsErrors)
					options.getCompilerArgs().add("-Werror");
			}
		}
	}

	private String getGithubRepo() {
		String vcsUrl = getVcsUrl();
		int lastIndexOfSlash = vcsUrl.lastIndexOf('/');
		int secondLast = vcsUrl.lastIndexOf('/', lastIndexOfSlash - 1);
		if (secondLast == -1)
			secondLast = vcsUrl.lastIndexOf(':', lastIndexOfSlash - 1);
		return vcsUrl.substring(secondLast + 1, vcsUrl.lastIndexOf('.'));
	}

	@SneakyThrows
	private String getVcsUrl() {
		return "git@github.com:" + settings.organisation + '/' + project.getRootProject().getName() + ".git";
	}

	@SneakyThrows
	private void configure() {
		initialised = true;
		Project project = this.project;

		project.getRepositories().jcenter();
		project.getRepositories().maven(it -> it.setUrl("https://repo.nallar.me/"));
		val javaPluginConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
		val sourceSets = javaPluginConvention.getSourceSets();

		val shouldApplyShipKit = shouldApplyShipKit();
		if (shouldApplyShipKit) {
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

			if (settings.minecraft != null)
				project.setVersion(settings.minecraft + '-' + project.getVersion().toString());

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
			pkg.setWebsiteUrl(getWebsiteUrl());
			if (settings.licenses == null)
				throw new IllegalArgumentException("Must set settings.licenses when shipkit is enabled");
			pkg.setLicenses(settings.licenses);
			if (settings.labels == null)
				throw new IllegalArgumentException("Must set labels when shipkit is enabled");
			pkg.setLabels(settings.labels);
			if (settings.description == null)
				throw new IllegalArgumentException("Must set description when shipkit is enabled");
			pkg.setDesc(settings.description);
			pkg.setGithubRepo(githubRepo);
			pkg.setIssueTrackerUrl("https://github.com/" + githubRepo + "/issues");

			if (settings.downstreamRepositories.size() != 0) {
				project.getPlugins().apply(CiUpgradeDownstreamPlugin.class);
				project.getExtensions().getByType(UpgradeDownstreamExtension.class).setRepositories(settings.downstreamRepositories);
			}

			if (isTaskRequested(UpgradeDependencyPlugin.PERFORM_VERSION_UPGRADE))
				project.getPlugins().apply(UpgradeDependencyPlugin.class);
		} else if (settings.shipkit) {
			project.getPlugins().apply(VersioningPlugin.class);
		}

		if (settings.noDocLint) {
			project.getTasks().all((it) -> {
				if (it instanceof Javadoc) {
					val options = ((Javadoc) it).getOptions();
					if (options instanceof CoreJavadocOptions) {
						((CoreJavadocOptions) options).addStringOption("Xdoclint:none", "-quiet");
					}
				}
			});
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
			if (settings.googleJavaFormat) {
				spotless.java(JavaExtension::googleJavaFormat);
			} else {
				File formatFile = getSpotlessFormatFile(project);
				spotless.java(it -> {
					it.eclipse().configFile(formatFile);
					it.removeUnusedImports();
					it.importOrder("java", "javax", "lombok", "sun", "org", "com", "org.minimallycorrect", "");
					it.endWithNewline();
				});
				project.getTasks().all(it -> {
					if (it.getName().startsWith("spotlessJava"))
						it.doFirst(new Action<Task>() {
							@Override
							public void execute(@NotNull Task ignored) {
								val resource = DefaultsPlugin.this.getClass().getResource("/spotless/eclipse-config.xml");
								try {
									if (!formatFile.exists() || resource.openConnection().getContentLength() != formatFile.length())
										try (val is = resource.openStream()) {
											val parent = formatFile.getParentFile();
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
						});
				});
			}
			if (settings.freshmark) {
				spotless.freshmark(it -> {
					it.properties(props -> props.putAll(settings.toProperties()));
					it.target(files("**/*.md"));
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
							spotless.kotlin(kotlin -> kotlin.ktlint().userData(Maps.of(
								"indent_style", "tab",
								"indent_size", "unset")));
						}
					}
				});
			}
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
			ForgeExtensions.configureMinecraft(project, settings, project.getExtensions().getByType(UserBaseExtension.class));
			if (settings.wrapperJavaArgs == null)
				settings.wrapperJavaArgs = "-Xmx2G";

			val map = new HashMap<String, String>();
			map.put("version", project.getVersion().toString());
			map.put("mcversion", settings.minecraft);

			val resources = project.getTasks().maybeCreate("processResources", ProcessResources.class);
			resources.getInputs().properties(map);
			resources.filesMatching("mcmod.info", it -> it.expand(map));

			val apiKey = System.getenv("CURSEFORGE_API_KEY");
			if (settings.curseforgeProject != null && apiKey != null) {
				CurseExtensions.applyCursePlugin(settings, project, shouldApplyShipKit, apiKey);
			}
		}

		if (settings.wrapperJavaArgs != null) {
			val wrapper = project.getTasks().maybeCreate("wrapper", Wrapper.class);
			wrapper.getInputs().property("javaArgs", settings.wrapperJavaArgs);
			wrapper.doLast(new Action<Task>() {
				@Override
				public void execute(Task ignored) {
					val optsEnvVar = "DEFAULT_JVM_OPTS";
					replace(wrapper.getScriptFile(), optsEnvVar + "=\"\"", optsEnvVar + "=\"" + settings.wrapperJavaArgs + "\"");
					replace(wrapper.getBatchScript(), "set " + optsEnvVar + "=", "set " + optsEnvVar + "=" + settings.wrapperJavaArgs);
				}
			});
		}
	}

	@NotNull
	private File getSpotlessFormatFile(Project project) {
		return new File(project.getBuildDir(), "spotless/eclipse-config.xml");
	}

	private FileTree files(String... globs) {
		val args = new HashMap<String, Object>();
		args.put("dir", project.getRootDir());
		args.put("includes", Arrays.asList(globs));
		args.put("excludes", Arrays.asList(GENERATED_PATHS));
		return project.fileTree(args);
	}

	private String getWebsiteUrl() {
		if (settings.websiteUrl != null)
			return settings.websiteUrl;
		return (settings.websiteUrl = "https://github.com/" + getGithubRepo());
	}

	private void addArtifact(String name, Object... files) {
		if (project.getTasks().findByName(name + "Jar") != null)
			return;

		val task = project.getTasks().create(name + "Jar", Jar.class);
		task.getArchiveClassifier().set(name);
		task.from(files);
		project.getArtifacts().add("archives", task);
	}

	private boolean shouldApplyShipKit() {
		return settings.shipkit && (project.hasProperty("applyShipkit") ||
			isTaskRequested("testRelease") ||
			isTaskRequested("initShipkit") ||
			isTaskRequested(UpgradeDependencyPlugin.PERFORM_VERSION_UPGRADE) ||
			Objects.equals(System.getenv("TRAVIS"), "true"));
	}

	private boolean isTaskRequested(String taskName) {
		return project.getGradle().getStartParameter().getTaskNames().equals(Collections.singletonList(taskName));
	}

	@Data
	public class Extension implements Callable<Void> {
		public final List<String> repos = new ArrayList<>(Arrays.asList(
			"https://repo.nallar.me/"));
		public final List<String> dependencyTargets = new ArrayList<>(Arrays.asList("compileOnly", "testCompileOnly", "annotationProcessor", "testAnnotationProcessor"));
		public final List<String> annotationDependencyCoordinates = new ArrayList<>(Arrays.asList(
			"com.google.code.findbugs:jsr305:3.0.2",
			"org.jetbrains:annotations:18.0.0"));
		public final List<String> lombokDependencyCoordinates = new ArrayList<>(Arrays.asList(
			"org.projectlombok:lombok:1.18.10"));
		public final List<String> downstreamRepositories = new ArrayList<>();
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
		public boolean freshmark = project.hasProperty("applyFreshmark") || isTaskRequested("performRelease");
		public boolean ktLint = true;
		public boolean ignoreSunInternalWarnings = false;
		public boolean treatWarningsAsErrors = true;
		public boolean noDocLint = true;

		@Override
		public Void call() {
			DefaultsPlugin.this.configure();
			return null;
		}

		Map<String, Object> toProperties() {
			val props = new HashMap<String, Object>();
			props.put("organisation", organisation);
			props.put("bintrayrepo", bintrayRepo);
			props.put("name", project.getName());
			props.put("group", project.getGroup());
			props.put("version", project.getVersion());
			if (licenses.length == 1)
				props.put("license", licenses[0]);
			props.put("licenses", Arrays.toString(licenses));
			props.put("releaseNotesPath", RELEASE_NOTES_PATH);
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
	}
}
