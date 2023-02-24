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

package net.fabricmc.loom.task.fernflower;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.task.AbstractDecompileTask;
import net.fabricmc.loom.util.GradleSupport;
import net.fabricmc.loom.util.LoomTaskExt;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecResult;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * A Gradle task that invokes Fernflower!
 * <p>
 * Created by covers1624 on 9/02/19.
 */
public class FernFlowerTask extends AbstractDecompileTask implements LoomTaskExt {
	public FernFlowerTask() {
		setGroup(Constants.TASK_GROUP_PLUMBING);
		setDescription("Runs the Fernflower decompiler on the Minecraft Forge jar.");
		getOutputs().upToDateWhen(t -> false);
	}
	
	private int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

	@TaskAction
	public void doTask() throws Throwable {
		List<String> args = new ArrayList<>();
		
		//fernflower options
		args.add("-" + IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES + "=1");
		args.add("-" + IFernflowerPreferences.BYTECODE_SOURCE_MAPPING + "=1");
		args.add("-" + IFernflowerPreferences.REMOVE_SYNTHETIC + "=1");
		args.add("-" + IFernflowerPreferences.LOG_LEVEL + "=warn");
		args.add("-" + IFernflowerPreferences.THREADS + "=" + getNumThreads());
		
		//ForkedFFExecutor wrapper options
		args.add("-input=" + getInput().getAbsolutePath());
		args.add("-output=" + getOutput().getAbsolutePath());
		getLibraries().forEach(f -> args.add("-library=" + f.getAbsolutePath()));
		if(getLoomGradleExtension().getProviderGraph().tinyMappingsFile != null) {
			args.add("-mappings=" + getLoomGradleExtension().getProviderGraph().tinyMappingsFile.toAbsolutePath());
		}
		if(getLineMapFile() != null) {
			args.add("-linemap=" + getLineMapFile().getAbsolutePath());
		}
		
		if(getProject().hasProperty("voldeloom.saferFernflower")) args.add("-safer-bytecode-provider");
		
		getLogging().captureStandardOutput(LogLevel.LIFECYCLE);
		ExecResult result = forkedJavaexec(spec -> {
			GradleSupport.setMainClass(spec, ForkedFFExecutor.class.getName());
			
			//spec.jvmArgs("-Xms200m", "-Xmx3G"); //the defaults work on my machine :tm: and this version of minecraft is so small and cute
			spec.setArgs(args);
			spec.setErrorOutput(System.err);
			spec.setStandardOutput(System.out);
		});

		result.rethrowFailure();
		result.assertNormalExitValue();
	}

	@Input
	public int getNumThreads() {
		return numThreads;
	}

	public void setNumThreads(int numThreads) {
		this.numThreads = numThreads;
	}
}
