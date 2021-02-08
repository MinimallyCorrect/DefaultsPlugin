plugins {
	id("java-gradle-plugin")
	id("maven-publish")
	id("com.gradle.plugin-publish") version "0.12.0"
	id("org.shipkit.shipkit-auto-version") version "1.1.1"
	id("org.shipkit.shipkit-changelog") version "1.1.4"
	id("org.shipkit.shipkit-github-release") version "1.1.4"
}

group = "org.minimallycorrect.gradle"

apply(from = "$rootDir/gradle/shipkit.gradle")

repositories {
	mavenCentral()
	maven(url = "https://plugins.gradle.org/m2/")
}

configurations.all { resolutionStrategy.cacheChangingModulesFor(30, "seconds") }

dependencies {
	api(gradleApi())
	compileOnly("org.jetbrains:annotations:20.1.0")
	implementation("com.diffplug.spotless:spotless-plugin-gradle:5.9.0")
	annotationProcessor("com.github.bsideup.jabel:jabel-javac-plugin:0.3.0")
}

tasks.withType<Javadoc> {
	(options as? CoreJavadocOptions)?.addStringOption("Xdoclint:none", "-quiet")
}

// per jabel docs
tasks.withType<JavaCompile> {
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
	plugins {
		create("DefaultsPlugin") {
			id = "org.minimallycorrect.gradle.DefaultsPlugin"
			implementationClass = "org.minimallycorrect.gradle.DefaultsPlugin"
			description = "Sets up sensible defaults for our gradle projects to avoid boilerplate "
		}
	}
}

pluginBundle {
	website = "http://www.minimallycorrect.org/"
	vcsUrl = "https://github.com/MinimallyCorrect/DefaultsPlugin"
	tags = listOf("gradle", "gradle-plugin")
}
