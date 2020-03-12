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

package net.fabricmc.loom.processors;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.forge.ASMFixesProcessor;
import net.fabricmc.loom.forge.CursedLibDeleterProcessor;

public class JarProcessorManager {
	private final Project project;
	private final LoomGradleExtension extension;

	private final List<JarProcessor> jarProcessors;

	public JarProcessorManager(Project project) {
		this.project = project;
		this.extension = project.getExtensions().getByType(LoomGradleExtension.class);
		jarProcessors = setupProcessors();
	}

	//TODO possibly expand via an API?
	private List<JarProcessor> setupProcessors() {
		List<JarProcessor> jarProcessors = new ArrayList<>();

		jarProcessors.add(new CursedLibDeleterProcessor());
		jarProcessors.add(new ASMFixesProcessor());
		jarProcessors.forEach(jarProcessor -> jarProcessor.setup(project, extension));
		return Collections.unmodifiableList(jarProcessors);
	}

	public boolean active() {
		return !jarProcessors.isEmpty();
	}

	public boolean isInvalid(File file) {
		if (!file.exists()) {
			return true;
		}

		return jarProcessors.stream().anyMatch(jarProcessor -> jarProcessor.isInvalid(file));
	}

	public void process(File file) {
		System.out.println("process");
		for (JarProcessor jarProcessor : jarProcessors) {
			jarProcessor.process(file);
		}
	}

	public <T extends JarProcessor> T getByType(Class<T> tClass) {
		//noinspection unchecked
		return (T) jarProcessors.stream().filter(jarProcessor -> jarProcessor.getClass().equals(tClass)).findFirst().orElse(null);
	}
}
