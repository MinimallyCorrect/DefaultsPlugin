package dev.minco.gradle.changelog;

import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.io.File;
import java.util.Date;

@CacheableTask
public abstract class ChangelogTask extends DefaultTask {
	@Inject
	public ChangelogTask(ProviderFactory providerFactory, ObjectFactory objectFactory) {
		getReleaseDate().convention(providerFactory.provider(() -> new SimpleDateFormat("yyyy-MM-dd").format(new Date())));
		getToRevision().convention("HEAD");
		getGitRefs().convention(objectFactory.directoryProperty().fileValue(new File(".git/refs/")));
	}

	@Input
	public abstract Property<String> getVersion();

	@Input
	public abstract Property<String> getReleaseDate();

	@Input
	public abstract Property<String> getGithubUrl();

	@Input
	public abstract Property<String> getFromRevision();

	@Input
	public abstract Property<String> getToRevision();

	@OutputFile
	public abstract RegularFileProperty getOutputFile();

	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getGitRefs();

	@TaskAction
	public void generateReleaseNotes() throws IOException {
		var commits = ChangelogBuilder.extractCommits(getFromRevision().get(), getToRevision().get());
		var changelog = ChangelogBuilder.formatChangelog(commits, getVersion().get(), getFromRevision().get(), getToRevision().get(), getGithubUrl().get(), getReleaseDate().get());

		Files.writeString(getOutputFile().get().getAsFile().toPath(), changelog);
	}
}
