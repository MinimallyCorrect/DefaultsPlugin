plugins {
	id("java-gradle-plugin")
	id("maven-publish")
	id("com.gradle.plugin-publish") version "1.2.2"
	id("org.shipkit.shipkit-auto-version") version "2.0.10"
	id("org.shipkit.shipkit-changelog") version "2.0.1"
	id("org.shipkit.shipkit-github-release") version "2.0.1"
}

apply(from = "$rootDir/properties.gradle")
apply(from = "$rootDir/gradle/shipkit.gradle")

group = "dev.minco.gradle"
val releasing = project.hasProperty("releasing")
if (!releasing) {
	version = "$version-SNAPSHOT"
}

repositories {
	mavenCentral()
	gradlePluginPortal()
}

dependencies {
	api(gradleApi())
	compileOnly("org.jetbrains:annotations:24.1.0")
	implementation("com.diffplug.spotless:spotless-plugin-gradle:7.0.1")
	// FIXME: Revert to upstream if https://github.com/bsideup/jabel/issues/182#issuecomment-1898900307 is ever closed
	annotationProcessor("com.pkware.jabel:jabel-javac-plugin:1.0.1-1")
	testAnnotationProcessor("com.pkware.jabel:jabel-javac-plugin:1.0.1-1")
	//annotationProcessor("com.github.bsideup.jabel:jabel-javac-plugin:1.0.0")
	//testAnnotationProcessor("com.github.bsideup.jabel:jabel-javac-plugin:1.0.0")

	testImplementation(platform("org.junit:junit-bom:5.11.0"))
	testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
	useJUnitPlatform()
}

java {
	targetCompatibility = JavaVersion.VERSION_11
	sourceCompatibility = JavaVersion.VERSION_11
	withSourcesJar()
	withJavadocJar()
}

tasks.withType<Javadoc>().configureEach {
	(options as? CoreJavadocOptions)?.addStringOption("Xdoclint:none", "-quiet")
}

// per jabel docs
tasks.withType<JavaCompile>().configureEach {
	targetCompatibility = JavaVersion.VERSION_11.toString()
	sourceCompatibility = JavaVersion.VERSION_11.toString()

	options.setDeprecation(true)
	options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-path", "-Xlint:-processing", "-Xlint:-serial"))

	options.compilerArgs.addAll(listOf(
		"--release", "11", // Avoid using Java 9+ APIs
		"--enable-preview"
	))
	// The following line can be omitted on Java 14 and higher
	options.compilerArgs.add("-Xplugin:jabel")

	doFirst {
		options.compilerArgs.remove("--enable-preview")
	}
}

gradlePlugin {
	// https://github.com/gradle/gradle/issues/24458#issuecomment-1713231089
	// pluginBundle.{website,vcsUrl,tags} moved to gradlePlugin in gradle 8.0 without proper announcement
	website = project.ext["website"] as String
	vcsUrl = project.ext["vcsUrl"] as String
	plugins {
		create(rootProject.name) {
			id = "$group.${rootProject.name}"
			displayName = rootProject.name
			implementationClass = "dev.minco.gradle.DefaultsPlugin"
			description = "Sets up sensible defaults for our gradle projects to avoid boilerplate "
			@Suppress("UNCHECKED_CAST")
			tags = project.ext["tags"] as List<String>
		}
	}
}

publishing {
	publications.withType<MavenPublication> {
		pom.scm {
			url.set(gradlePlugin.vcsUrl)
		}
	}

	repositories {
		System.getenv("DEPLOYMENT_REPO_PASSWORD")?.let { deploymentRepoPassword ->
			maven {
				url = if (releasing) {
					name = "minco.dev_releases"
					uri(System.getenv("DEPLOYMENT_REPO_URL_RELEASE"))
				} else {
					name = "minco.dev_snapshots"
					uri(System.getenv("DEPLOYMENT_REPO_URL_SNAPSHOT"))
				}
				credentials {
					username = System.getenv("DEPLOYMENT_REPO_USERNAME")
					password = deploymentRepoPassword
				}
			}
		}
	}
}
