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

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;

import java.io.File;

public abstract class AbstractDecompileTask extends AbstractLoomTask {
	private Object input;
	private Object output;
	private Object lineMapFile;
	private Object libraries;

	@InputFile
	public File getInput() {
		return getProject().file(input);
	}

	@OutputFile
	public File getOutput() {
		return getProject().file(output);
	}

	@OutputFile
	public File getLineMapFile() {
		return getProject().file(lineMapFile);
	}

	@InputFiles
	public FileCollection getLibraries() {
		return getProject().files(libraries);
	}

	public void setInput(Object input) {
		this.input = input;
	}

	public void setOutput(Object output) {
		this.output = output;
	}

	public void setLineMapFile(Object lineMapFile) {
		this.lineMapFile = lineMapFile;
	}

	public void setLibraries(Object libraries) {
		this.libraries = libraries;
	}
}
