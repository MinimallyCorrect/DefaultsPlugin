plugins {
	id("java-gradle-plugin")
	id("maven-publish")
	id("com.gradle.plugin-publish") version "0.16.0"
	id("org.shipkit.shipkit-auto-version") version "1.1.19"
	id("org.shipkit.shipkit-changelog") version "1.1.15"
	id("org.shipkit.shipkit-github-release") version "1.1.15"
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
	compileOnly("org.jetbrains:annotations:22.0.0")
	implementation("com.diffplug.spotless:spotless-plugin-gradle:5.14.2")
	annotationProcessor("com.github.bsideup.jabel:jabel-javac-plugin:0.4.1")
	testAnnotationProcessor("com.github.bsideup.jabel:jabel-javac-plugin:0.4.1")

	testImplementation(platform("org.junit:junit-bom:5.8.0"))
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
		create(rootProject.name) {
			id = "$group.${rootProject.name}"
			displayName = rootProject.name
			implementationClass = "dev.minco.gradle.DefaultsPlugin"
			description = "Sets up sensible defaults for our gradle projects to avoid boilerplate "
		}
	}
}

pluginBundle {
	website = project.ext["website"] as String
	vcsUrl = project.ext["vcsUrl"] as String
	@Suppress("UNCHECKED_CAST")
	tags = project.ext["tags"] as List<String>
}

publishing {
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
