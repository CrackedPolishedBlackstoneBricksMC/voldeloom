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

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.LoomTaskExt;
import net.fabricmc.loom.util.VoldeloomFileHelpers;
import net.fabricmc.loom.util.WellKnownLocations;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

public class CleanLoomBinaries extends DefaultTask implements LoomTaskExt {
	public CleanLoomBinaries() {
		setGroup("fabric");
	}
	
	@TaskAction
	public void run() {
		LoomGradleExtension extension = getLoomGradleExtension();
		
		//TODO: replace this with a generic "clean" system
		VoldeloomFileHelpers.delete(getProject(),
			extension.getDependencyManager().getMinecraftMergedProvider().getMergedJar(),
			extension.getDependencyManager().getMinecraftForgePatchedProvider().getPatchedJar(),
			extension.getDependencyManager().getMinecraftForgeMappedProvider().getIntermediaryJar(),
			extension.getDependencyManager().getMinecraftForgeMappedProvider().getMappedJar(),
			extension.getDependencyManager().getMinecraftLibraryProvider().getNativesDir(),
			WellKnownLocations.getNativesJarStore(getProject())
		);
	}
}
