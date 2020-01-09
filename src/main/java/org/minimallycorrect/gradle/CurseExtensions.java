package org.minimallycorrect.gradle;

import java.io.IOException;
import java.util.List;

import lombok.val;

import org.gradle.api.Project;

import com.google.common.io.Files;
import com.jfrog.bintray.gradle.tasks.BintrayUploadTask;
import com.matthewprenger.cursegradle.CurseExtension;
import com.matthewprenger.cursegradle.CurseGradlePlugin;
import com.matthewprenger.cursegradle.CurseProject;
import com.matthewprenger.cursegradle.CurseUploadTask;

public class CurseExtensions {
	static void maybeAddArtifact(Project project, String name, CurseProject curseProject) {
		val curseforge = project.getTasks().findByName("curseforge");
		val task = project.getTasks().findByName(name);
		if (curseforge == null || task == null)
			return;
		curseforge.dependsOn(task);
		curseProject.addArtifact(task);
	}

	@SuppressWarnings("unchecked")
	static void applyCursePlugin(DefaultsPlugin.Extension settings, Project project, String apiKey) {
		project.getPlugins().apply(CurseGradlePlugin.class);
		val extension = project.getExtensions().getByType(CurseExtension.class);
		extension.setApiKey(apiKey);
		val curseProject = new CurseProject();
		curseProject.setId(settings.curseforgeProject);
		curseProject.setApiKey(apiKey);
		curseProject.setChangelog(new FileReader(project));
		curseProject.setReleaseType("beta");
		//noinspection unchecked
		curseProject.setGameVersionStrings((List<Object>) (List<?>) ForgeExtensions.getSupportedVersions(settings.minecraft));
		maybeAddArtifact(project, "sourceJar", curseProject);
		maybeAddArtifact(project, "deobfJar", curseProject);
		maybeAddArtifact(project, "javadocJar", curseProject);
		extension.getCurseProjects().add(curseProject);

		project.getTasks().withType(BintrayUploadTask.class).all(it -> it.dependsOn("reobfJar"));

		project.getTasks().withType(CurseUploadTask.class).all(it -> it.dependsOn("updateReleaseNotes"));

		project.getTasks().matching(it -> it.getName().equals("performRelease")).all(it -> it.dependsOn("curseforge"));
	}

	private static class FileReader {
		private final Project project;
		String cached;

		FileReader(Project project) {
			this.project = project;
		}

		@Override
		public String toString() {
			String cached = this.cached;
			if (cached != null)
				return cached;
			try {
				cached = Files.asCharSource(project.file(DefaultsPlugin.RELEASE_NOTES_PATH), DefaultsPlugin.CHARSET).read();
			} catch (IOException e) {
				cached = "Failed to read changelog from " + project.file(DefaultsPlugin.RELEASE_NOTES_PATH);
				project.getLogger().error(cached, e);
			}
			return (this.cached = cached);
		}
	}
}
