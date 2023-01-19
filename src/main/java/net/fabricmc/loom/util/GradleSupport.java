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
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.internal.Pair;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;

/**
 * Bridges the gap over large gradle api changes. 
 */
public class GradleSupport {
	public static String compileOrImplementation;
	public static String runtimeOrRuntimeOnly;
	
	//(VOLDELOOM-DISASTER) Gradle 7 decided to rename "compile" to "implementation" and "runtime" to "runtimeOnly"
	//They're basically the same thing, so we can just swap out the names as-appropriate.
	public static void init(Project project) {
		Set<String> names = project.getConfigurations().getNames();
		
		if(names.contains("compile")) {
			compileOrImplementation = "compile";
		} else if(names.contains("implementation")) {
			compileOrImplementation = "implementation";
		} else {
			throw new IllegalStateException("Not sure what the name of the compilation configuration is (apparently not `compile` or `implementation`)");
		}
		
		if(names.contains("runtime")) {
			runtimeOrRuntimeOnly = "runtime";
		} else if(names.contains("runtimeOnly")) {
			runtimeOrRuntimeOnly = "runtimeOnly";
		} else {
			throw new IllegalStateException("Not sure what the name of the runtime-only configuration is (apparently not `runtime` or `runtimeOnly`)");
		}
		
		project.getLogger().info("We're on a '" + compileOrImplementation + "'-flavored Gradle; slight aftertaste of '" + runtimeOrRuntimeOnly + "'.");
	}
	
	public static Configuration getCompileOrImplementationConfiguration(ConfigurationContainer configurations) {
		return configurations.getByName(compileOrImplementation);
	}
	
	public static RegularFileProperty getRegularFileProperty(Project project) {
		try {
			//First try the new method,
			return getRegularFilePropertyModern(project);
		} catch (Exception e) {
			try {
				//if that fails fall back.
				return getRegularFilePropertyLegacy(project);
			} catch (Exception ee) {
				throw new RuntimeException("Unable to getfileProperty... pensive");
			}
		}
	}
	
	private static RegularFileProperty getRegularFilePropertyModern(Project project) throws ReflectiveOperationException {
		ObjectFactory objectFactory = project.getObjects();
		return (RegularFileProperty) getAccessibleMethod(objectFactory.getClass(), "fileProperty")
			.invoke(objectFactory);
	}
	
	private static RegularFileProperty getRegularFilePropertyLegacy(Project project) throws ReflectiveOperationException {
		ProjectLayout layout = project.getLayout();
		return (RegularFileProperty) getAccessibleMethod(layout.getClass(), "fileProperty")
			.invoke(layout);
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
					getAccessibleMethod(obj.getClass(), "includeGroup", String.class).invoke(obj, includeGroup);
				} catch (ReflectiveOperationException e) {
					throw new RuntimeException(e);
				}
			};
			contentMethod.setAccessible(true);
			contentMethod.invoke(repo, erasedAction);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static void setMainClass(JavaExec task, String mainClass) {
		try {
			setMainClassModern(task, mainClass);
		} catch (ReflectiveOperationException ignore) {
			setMainClassLegacy(task, mainClass);
		}
	}
	
	private static void setMainClassModern(JavaExec task, String mainClass) throws ReflectiveOperationException {
		Method getMainClassMethod = task.getClass().getMethod("getMainClass");
		getMainClassMethod.setAccessible(true);
		
		@SuppressWarnings("unchecked")
		Property<String> mainClassProp = (Property<String>) getMainClassMethod.invoke(task);
		mainClassProp.set(mainClass);
	}
	
	@SuppressWarnings("deprecation") //The method still exists in Gradle 7, but it is deprecated and doesn't function correctly.
	private static void setMainClassLegacy(JavaExec task, String mainClass) {
		task.setMain(mainClass);
	}
	
	@SuppressWarnings({
		"rawtypes", "unchecked", //Property<?> and Provider<?> rawtypes, lets me call set() when I can't name the type.
		"UnstableApiUsage", //Most of the API was marked unstable in Gradle 4. It got stabilized as-is in 7...
		"RedundantSuppression" //...so when i'm using Gradle 7, the UnstableApiUsage warning is redundant.
	})
	public static boolean trySetJavaToolchain(JavaExec task, @Nullable JavaVersion javaVersion, @Nullable String vendorString) {
		task.getLogger().info("] Trying to set up a Java {} {} toolchain for {}", javaVersion, vendorString, task.getName());
		
		Class<?> javaLanguageVersionClass;
		Class<?> javaToolchainServiceClass;
		Class<?> jvmVendorSpecClass;
		Class<?> defaultToolchainSpecClass;
		Class<?> javaToolchainSpecClass;
		
		try {
			javaLanguageVersionClass = Class.forName("org.gradle.jvm.toolchain.JavaLanguageVersion");
			javaToolchainServiceClass = Class.forName("org.gradle.jvm.toolchain.JavaToolchainService");
			jvmVendorSpecClass = Class.forName("org.gradle.jvm.toolchain.JvmVendorSpec");
			defaultToolchainSpecClass = Class.forName("org.gradle.jvm.toolchain.internal.DefaultToolchainSpec");
			javaToolchainSpecClass = Class.forName("org.gradle.jvm.toolchain.JavaToolchainSpec");
		} catch (ClassNotFoundException e) {
			task.getLogger().info("\\-> Just kidding, looks like this version of Gradle doesn't have all the necessary toolchain bits. ({})", e.getMessage());
			return false; //Doesn't support toolchains
		}
		
		try {
			Object javaToolchainSpec = task.getProject().getObjects().newInstance(defaultToolchainSpecClass);
			
			//configure language version
			if(javaVersion != null) {
				int javaVersionString = javaVersion.ordinal() + 1; //SORRY ITS WEIRD
				Property languageVersionProperty = (Property) getAccessibleMethod(javaToolchainSpec.getClass(), "getLanguageVersion").invoke(javaToolchainSpec);
				languageVersionProperty.set(getAccessibleMethod(javaLanguageVersionClass, "of", int.class).invoke(null, javaVersionString));
			}
			
			//configure vendor
			if(vendorString != null && !"ANY".equals(vendorString)) {
				Property vendorProperty = (Property) getAccessibleMethod(javaToolchainSpec.getClass(), "getVendor").invoke(javaToolchainSpec);
				
				Field vendorField;
				try {
					vendorField = getAccessibleField(jvmVendorSpecClass, vendorString);
				} catch (ReflectiveOperationException e) {
					throw new RuntimeException("Unknown JVM vendor '" + vendorString + "'. Note that Voldeloom uses some weird hacks to get the vendor, might not be your fault");
				}
				
				vendorProperty.set(vendorField.get(null));
			}
			
			//create a launcher toolchain
			Object javaToolchainService = task.getProject().getExtensions().getByType(javaToolchainServiceClass);
			Provider launcherProvider = (Provider) getAccessibleMethod(javaToolchainService.getClass(), "launcherFor", javaToolchainSpecClass).invoke(javaToolchainService, javaToolchainSpec);
			
			//set the task to use this toolchain
			Property javaLauncherProperty = (Property) getAccessibleMethod(task.getClass(), "getJavaLauncher").invoke(task);
			javaLauncherProperty.set(launcherProvider);
			
			task.getLogger().info("\\-> Done.");
			return true;
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Reflection exception while configuring JavaToolchainSpec", e);
		}
	}
	
	public static JavaVersion convertToJavaVersion(Object o) {
		//This will handle integers, strings, JavaVersions, and (through an accident of
		//how toString is implemented on it) the Gradle 6 JavaLanguageVersion object too.
		//The method is mostly here in GradleSupport for consistency with convertToVendorString.
		return JavaVersion.toVersion(o);
	}
	
	public static String convertToVendorString(Object o) {
		if(o instanceof String) {
			return ((String) o).toUpperCase(Locale.ROOT);
		}
		
		//Maybe we're on Gradle 7 and this is a JvmVendorSpec.ADOPTOPENJDK sort of thing?
		if(o.getClass().getName().equals("org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec")) {
			//As of Gradle 7.6, these have a toString() that describes what i want
			return (o.toString()).toUpperCase(Locale.ROOT);
		}
		
		throw new IllegalArgumentException("[Voldeloom GradleSupport] Not sure how to parse this " + o.getClass() + " as a JVM vendor.");
	}
	
	public static Pair<JavaVersion, String> readToolchainSpec(Object javaToolchainSpec) {
		try {
			Method getLanguageVersion = getAccessibleMethod(javaToolchainSpec.getClass(), "getLanguageVersion");
			Property<?> languageVersionProperty = (Property<?>) getLanguageVersion.invoke(javaToolchainSpec);
			Object languageVersion = languageVersionProperty.getOrNull();
			JavaVersion javaVersion = convertToJavaVersion(languageVersion == null ? 8 : languageVersion);
			
			Method getVendor = getAccessibleMethod(javaToolchainSpec.getClass(), "getVendor");
			Property<?> vendorProperty = (Property<?>) getVendor.invoke(javaToolchainSpec);
			Object vendor = vendorProperty.getOrNull();
			String vendorString = convertToVendorString(vendor == null ? "ADOPTIUM" : vendor);
			
			return Pair.of(javaVersion, vendorString);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("[Voldeloom GradleSupport] Unable to readToolchainSpec this " + javaToolchainSpec.getClass());
		}
	}
	
	private static Method getAccessibleMethod(Class<?> classs, String method, Class<?>... types) throws ReflectiveOperationException {
		Method m = classs.getMethod(method, types);
		m.setAccessible(true);
		return m;
	}
	
	private static Field getAccessibleField(Class<?> classs, String field) throws ReflectiveOperationException {
		Field f = classs.getField(field);
		f.setAccessible(true);
		return f;
	}
}
