plugins {
	id("java-gradle-plugin")
	id("maven-publish")
	id("com.gradle.plugin-publish") version "0.12.0"
	id("org.shipkit.shipkit-auto-version") version "1.1.1"
	id("org.shipkit.shipkit-changelog") version "1.1.4"
	id("org.shipkit.shipkit-github-release") version "1.1.4"
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
	compileOnly("org.jetbrains:annotations:20.1.0")
	implementation("com.diffplug.spotless:spotless-plugin-gradle:5.10.1")
	annotationProcessor("com.github.bsideup.jabel:jabel-javac-plugin:0.3.0")
}

java {
	withSourcesJar()
	withJavadocJar()
}

tasks.withType<Javadoc>().configureEach {
	(options as? CoreJavadocOptions)?.addStringOption("Xdoclint:none", "-quiet")
}

// per jabel docs
tasks.withType<JavaCompile>().configureEach {
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
	website = "http://www.minimallycorrect.org/"
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
		System.getenv("GITHUB_TOKEN")?.let { githubToken ->
			maven {
				name = "github_packages"
				url = uri("https://maven.pkg.github.com/${project.ext["githubOwnerProject"]}")
				credentials(HttpHeaderCredentials::class.java) {
					name = "Authorization"
					value = "Bearer $githubToken"
				}
				authentication {
					create<HttpHeaderAuthentication>("header")
				}
			}
		}
	}
}
