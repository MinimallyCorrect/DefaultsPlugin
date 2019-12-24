package org.minimallycorrect.gradle;

import lombok.val;

import org.gradle.api.Project;

import com.matthewprenger.cursegradle.CurseProject;

public class CurseExtensions {
	static void maybeAddArtifact(Project project, String name, CurseProject curseProject) {
		val curseforge = project.getTasks().findByName("curseforge");
		val task = project.getTasks().findByName(name);
		if (curseforge == null || task == null)
			return;
		curseforge.dependsOn(task);
		curseProject.addArtifact(task);
	}
}
