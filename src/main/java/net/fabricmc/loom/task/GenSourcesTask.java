package net.fabricmc.loom.task;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.task.fernflower.ForkedFFExecutor;
import net.fabricmc.loom.util.GradleSupport;
import net.fabricmc.loom.util.LineNumberRemapper;
import net.fabricmc.loom.util.LoomTaskExt;
import org.gradle.api.DefaultTask;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecResult;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class GenSourcesTask extends DefaultTask implements LoomTaskExt {
	public GenSourcesTask() {
		setGroup(Constants.TASK_GROUP_TOOLS);
		setDescription("Decompile Minecraft and Forge using the Fernflower decompiler. The resulting file may be attached to your IDE to provide a better Minecraft-browsing experience.");
		getOutputs().upToDateWhen(__ -> false);
	}
	
	private int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
	
	@TaskAction
	public void doIt() throws Exception {
		Path mappedJar = getLoomGradleExtension().getProviderGraph().finishedJar;
		Collection<Path> libraries = getLoomGradleExtension().getProviderGraph().mcNonNativeDependencies_Todo;
		
		/// fernflower ///
		getLogger().lifecycle("|-> 1. Fernflower.");
		
		Path sourcesJar = LoomGradlePlugin.replaceExtension(mappedJar, "-sources-no-linemap.jar");
		Path linemapFile = LoomGradlePlugin.replaceExtension(mappedJar, "-sources.lmap");
		
		Files.deleteIfExists(sourcesJar);
		Files.deleteIfExists(linemapFile);
		
		getLogger().lifecycle("] sources jar target: {}", sourcesJar);
		getLogger().lifecycle("] linemap file target: {}", linemapFile);
		
		getLogger().lifecycle("|-> Configuring Fernflower...");
		List<String> args = new ArrayList<>();
		
		//fernflower options
		args.add("-" + IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES + "=1");
		args.add("-" + IFernflowerPreferences.BYTECODE_SOURCE_MAPPING + "=1");
		args.add("-" + IFernflowerPreferences.REMOVE_SYNTHETIC + "=1");
		args.add("-" + IFernflowerPreferences.LOG_LEVEL + "=warn");
		args.add("-" + IFernflowerPreferences.THREADS + "=" + getNumThreads());
		
		//ForkedFFExecutor wrapper options
		args.add("-input=" + mappedJar.toAbsolutePath());
		args.add("-output=" + sourcesJar.toAbsolutePath());
		libraries.forEach(f -> args.add("-library=" + f.toAbsolutePath()));
		if(getLoomGradleExtension().getProviderGraph().tinyMappingsFile != null) {
			args.add("-mappings=" + getLoomGradleExtension().getProviderGraph().tinyMappingsFile.toAbsolutePath());
		}
		args.add("-linemap=" + linemapFile.toAbsolutePath());
		if(getProject().hasProperty("voldeloom.saferFernflower")) args.add("-safer-bytecode-provider");
		
		getLogger().lifecycle("|-> Starting ForkedFFExector...");
		getLogging().captureStandardOutput(LogLevel.LIFECYCLE);
		ExecResult result = forkedJavaexec(spec -> {
			GradleSupport.setMainClass(spec, ForkedFFExecutor.class.getName());
			
			//spec.jvmArgs("-Xms200m", "-Xmx3G"); //the defaults work on my machine :tm: and this version of minecraft is so small and cute
			spec.setArgs(args);
			spec.setErrorOutput(System.err);
			spec.setStandardOutput(System.out);
		});
		getLogger().lifecycle("|-> Exec finished?");
		result.rethrowFailure();
		result.assertNormalExitValue();
		getLogger().lifecycle("|-> Exec success!");
		
		/// linemapping ///
		getLogger().lifecycle("|-> 2. Line number remapping.");
		
		Path linemappedJar = LoomGradlePlugin.replaceExtension(mappedJar, "-sources.jar");
		
		Files.deleteIfExists(linemappedJar);
		
		getLogger().lifecycle("] linemapped jar target: {}", linemappedJar);
		
		getLogger().lifecycle("|-> Configuring LineNumberRemapper...");
		LineNumberRemapper remapper = new LineNumberRemapper();
		remapper.readMappings(linemapFile.toFile());
		
		getLogger().lifecycle("|-> Remapping line numbers...");
		try (FileSystem in = FileSystems.newFileSystem(URI.create("jar:" + sourcesJar.toUri()), Collections.emptyMap());
		     FileSystem out = FileSystems.newFileSystem(URI.create("jar:" + linemappedJar.toUri()), Collections.singletonMap("create", "true"))) {
			remapper.process(getLogger(), in.getPath("/"), out.getPath("/"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		getLogger().lifecycle("|-> Done. Finished sources jar is at {} - enjoy", linemappedJar);
	}
	
	@Input
	public int getNumThreads() {
		return numThreads;
	}
	
	public void setNumThreads(int numThreads) {
		this.numThreads = numThreads;
	}
}
