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

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

//This is used to bridge the gap over large gradle api changes.
public class GradleSupport {
	//(WEIRD VOLDELOOM STUFF)
	public static String compileOrImplementation;
	public static String runtimeOrRuntimeOnly;
	
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
	
	//(VOLDELOOM-DISASTER) Gradle 7 decided to rename "compile" to "implementation" and "runtime" to "runtimeOnly"
	//They're basically the same thing, so we can just swap out the names as-appropriate.
	public static void init(Project project) {
		try {
			project.getConfigurations().getByName("compile");
			compileOrImplementation = "compile";
			runtimeOrRuntimeOnly = "runtime";
		} catch (UnknownDomainObjectException e) {
			compileOrImplementation = "implementation";
			runtimeOrRuntimeOnly = "runtimeOnly";
		}
	}
	
	public static Configuration getCompileOrImplementationConfiguration(ConfigurationContainer configurations) {
		return configurations.getByName(compileOrImplementation);
	}
	
	//(VOLDELOOM-DISASTER) includeGroup is an optimization that avoids making spurious HTTP requests to unrelated repos.
	//This doesn't exist in Gradle 4, which this project currently compiles against.
	public static void maybeSetIncludeGroup(ArtifactRepository repo, String includeGroup) {
		Method contentMethod;
		try {
			contentMethod = repo.getClass().getMethod("content", Action.class);
		} catch (NoSuchMethodException e) {
			//We expect this in gradle 4.x.
			return;
		}
		
		try {
			Action<Object> erasedAction = (obj) -> {
				try {
					Method what = obj.getClass().getMethod("includeGroup", String.class);
					what.setAccessible(true);
					what.invoke(obj, includeGroup);
				} catch (ReflectiveOperationException e) {
					throw new RuntimeException(e);
				}
			};
			contentMethod.invoke(repo, erasedAction);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}
}
