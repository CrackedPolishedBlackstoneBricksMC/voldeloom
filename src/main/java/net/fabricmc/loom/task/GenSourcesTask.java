package net.fabricmc.loom.task;

import net.fabricmc.loom.Constants;
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
	
	public static class SourceGenerationJob {
		public Path mappedJar;
		public Path sourcesJar;
		public Path linemapFile;
		public Path linemappedJar;
		public Collection<Path> libraries;
		public Path mcpMappingsZip;
	}
	
	@TaskAction
	public void doIt() throws Exception {
		List<SourceGenerationJob> jobs = getLoomGradleExtension().getProviderGraph().sourceGenerationJobs;
		for(SourceGenerationJob job : jobs) {
			Path mappedJar = job.mappedJar; //input
			Path sourcesJar = job.sourcesJar; //intermediary product
			Path linemapFile = job.linemapFile; //intermediary product
			Path linemappedJar = job.linemappedJar; //output
			Collection<Path> libraries = job.libraries; //resource
			Path mcpMappingsZip = job.mcpMappingsZip;
			
			Files.deleteIfExists(sourcesJar);
			Files.deleteIfExists(linemapFile);
			Files.deleteIfExists(linemappedJar);
			if(sourcesJar.getParent() != null) Files.createDirectories(sourcesJar.getParent());
			if(linemapFile.getParent() != null) Files.createDirectories(linemapFile.getParent());
			if(linemappedJar.getParent() != null) Files.createDirectories(linemappedJar.getParent());
			getLogger().lifecycle("] sources jar target: {}", sourcesJar);
			getLogger().lifecycle("] linemap file target: {}", linemapFile);
			getLogger().lifecycle("] linemapped jar target: {}", linemappedJar);
			
			/// fernflower ///
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
			if(mcpMappingsZip != null) args.add("-mcpmappings=" + mcpMappingsZip.toAbsolutePath());
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
			
			getLogger().lifecycle("|-> Configuring LineNumberRemapper...");
			LineNumberRemapper remapper = new LineNumberRemapper().readMappings(linemapFile);
			
			getLogger().lifecycle("|-> Remapping line numbers...");
			try (FileSystem src = FileSystems.newFileSystem(URI.create("jar:" + mappedJar.toUri()), Collections.emptyMap());
			     FileSystem dst = FileSystems.newFileSystem(URI.create("jar:" + linemappedJar.toUri()), Collections.singletonMap("create", "true"))) {
				remapper.process(src, dst, getLogger());
			} catch (Exception e) {
				throw new RuntimeException("Trouble linemapping: " + e);
			}
		}
		
		int n = jobs.size();
		getLogger().lifecycle("|-> Created {} sources jar{}.", n, n == 1 ? "" : "s");
		for(SourceGenerationJob job : jobs) getLogger().lifecycle("- {}", job.sourcesJar);
	}
	
	@Input
	public int getNumThreads() {
		return numThreads;
	}
	
	public void setNumThreads(int numThreads) {
		this.numThreads = numThreads;
	}
}
