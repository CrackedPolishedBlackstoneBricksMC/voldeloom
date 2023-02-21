/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.task;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.util.LineNumberRemapper;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.Collections;

/**
 * Gradle task used when decompiling the game that corrects line numbers in Fernflower's output.
 */
public class RemapLineNumbersTask extends DefaultTask {
	public RemapLineNumbersTask() {
		setGroup(Constants.TASK_GROUP_PLUMBING);
		setDescription("Corrects line numbers in Fernflower's output. This improves the experience of using breakpoints in the generated sources.");
		getOutputs().upToDateWhen(t -> false);
	}
	
	private Object input;
	private Object output;
	private Object lineMapFile;

	@TaskAction
	public void doTask() throws Throwable {
		Project project = getProject();

		project.getLogger().info(":adjusting line numbers");
		LineNumberRemapper remapper = new LineNumberRemapper();
		remapper.readMappings(getLineMapFile());

		try (FileSystem in = FileSystems.newFileSystem(URI.create("jar:" + getInput().toURI()), Collections.emptyMap());
		     FileSystem out = FileSystems.newFileSystem(URI.create("jar:" + getOutput().toURI()), Collections.singletonMap("create", "true"))) {
			remapper.process(project.getLogger(), in.getPath("/"), out.getPath("/"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@InputFile
	public File getInput() {
		return getProject().file(input);
	}

	@InputFile
	public File getLineMapFile() {
		return getProject().file(lineMapFile);
	}

	@OutputFile
	public File getOutput() {
		return getProject().file(output);
	}

	public void setInput(Object input) {
		this.input = input;
	}

	public void setLineMapFile(Object lineMapFile) {
		this.lineMapFile = lineMapFile;
	}

	public void setOutput(Object output) {
		this.output = output;
	}
}
