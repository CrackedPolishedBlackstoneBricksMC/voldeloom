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

package net.fabricmc.loom.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.gradle.api.Project;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;

//This is used to bridge the gap over large gradle api changes.
public class GradleSupport {
	public static RegularFileProperty getfileProperty(Project project) {
		try {
			//First try the new method,
			return getfilePropertyModern(project);
		} catch (Exception e) {
			try {
				//if that fails fall back.
				return getfilePropertyLegacy(project);
			} catch (Exception ee) {
				throw new RuntimeException("Unable to getfileProperty... pensive");
			}
		}
	}

	private static RegularFileProperty getfilePropertyModern(Project project) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		ObjectFactory objectFactory = project.getObjects();
		Method method = objectFactory.getClass().getDeclaredMethod("fileProperty");
		method.setAccessible(true);
		return (RegularFileProperty) method.invoke(objectFactory);
	}

	//VOLDELOOM-DISASTER: Rewrote this to use reflection too, so it at least compiles against modern Gradle
	private static RegularFileProperty getfilePropertyLegacy(Project project) throws ReflectiveOperationException {
		ProjectLayout layout = project.getLayout();
		Method method = layout.getClass().getDeclaredMethod("fileProperty");
		method.setAccessible(true);
		return (RegularFileProperty) method.invoke(layout);
	}
}
