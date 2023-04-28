package net.fabricmc.loom.task;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.task.fernflower.ForkedFFExecutor;
import net.fabricmc.loom.util.GradleSupport;
import net.fabricmc.loom.util.LineNumberRemapper;
import net.fabricmc.loom.util.LoomTaskExt;
import net.fabricmc.loom.util.ZipUtil;
import org.gradle.api.DefaultTask;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecResult;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GenSourcesTask extends DefaultTask implements LoomTaskExt {
	public GenSourcesTask() {
		setGroup(Constants.TASK_GROUP_TOOLS);
		setDescription("Decompile Minecraft and Forge using the Fernflower decompiler. The resulting file may be attached to your IDE to provide a better Minecraft-browsing experience.");
		getOutputs().upToDateWhen(__ -> false);
	}
	
	private int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
	private boolean saferBytecodeProvider = getProject().hasProperty("voldeloom.saferFernflower");
	
	//debugging/development options
	private boolean skipDecompile = getProject().hasProperty("voldeloom.skip-decompile");
	private boolean linemapDebug = getProject().hasProperty("voldeloom.linemap-debug");
	
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
			if(!skipDecompile) fernflower(job.mappedJar, job.sourcesJar, job.mcpMappingsZip, job.linemapFile, job.libraries);
			linemap(job.mappedJar, job.linemappedJar, job.linemapFile, /* linemapDebug only */ job.sourcesJar);
		}
		
		int n = jobs.size();
		getLogger().lifecycle("");
		getLogger().lifecycle("!! Created {} sources jar{}.", n, n == 1 ? "" : "s");
		for(SourceGenerationJob job : jobs) getLogger().lifecycle(" - {}", job.sourcesJar);
	}
	
	private void fernflower(Path mappedJar, Path sourcesJar, Path mcpMappingsZip, Path linemapFile, Collection<Path> libraries) throws IOException {
		getLogger().lifecycle("|-> Configuring Fernflower...");
		
		Files.deleteIfExists(sourcesJar);
		Files.deleteIfExists(linemapFile);
		if(sourcesJar.getParent() != null) Files.createDirectories(sourcesJar.getParent());
		if(linemapFile.getParent() != null) Files.createDirectories(linemapFile.getParent());
		getLogger().lifecycle("] sources jar target: {}", sourcesJar);
		getLogger().lifecycle("] linemap file target: {}", linemapFile);
		
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
		if(saferBytecodeProvider) args.add("-safer-bytecode-provider");
		
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
	}
	
	private void linemap(Path mappedJar, Path linemappedJar, Path linemapFile, Path sourcesJar) throws IOException {
		getLogger().lifecycle("|-> Configuring LineNumberRemapper...");
		
		Files.deleteIfExists(linemappedJar);
		if(linemappedJar.getParent() != null) Files.createDirectories(linemappedJar.getParent());
		getLogger().lifecycle("] linemapped jar target: {}", linemappedJar);
		
		LineNumberRemapper remapper = new LineNumberRemapper().readMappings(linemapFile);
		
		getLogger().lifecycle("|-> Remapping line numbers...");
		try(FileSystem srcFs = ZipUtil.openFs(mappedJar); FileSystem dstFs = ZipUtil.createFs(linemappedJar)) {
			remapper.process(srcFs, dstFs);
		} catch (Exception e) {
			throw new RuntimeException("Trouble linemapping: " + e.getMessage(), e);
		}
		getLogger().lifecycle("|-> Done linemapping.");
		
		if(linemapDebug) {
			Path debugSources = LoomGradlePlugin.replaceExtension(sourcesJar, ".linemap-debug.jar");
			
			Files.deleteIfExists(debugSources);
			if(debugSources.getParent() != null) Files.createDirectories(debugSources.getParent());
			getLogger().lifecycle("] !! DEBUGGING !!, sources jar annotated with linemap data: {}", debugSources);
			
			getLogger().lifecycle("|-> Creating linemap debug jar...");
			try(FileSystem sourcesFs = ZipUtil.openFs(sourcesJar); FileSystem processedSourcesFs = ZipUtil.createFs(debugSources)) {
				remapper.processDebug(sourcesFs, processedSourcesFs);
			} catch (Exception e) {
				throw new RuntimeException("Trouble writing linemap debug jar: " + e.getMessage(), e);
			}
			getLogger().lifecycle("|-> Linemap debug jar created.");
		}
	}
	
	@Input
	public int getNumThreads() {
		return numThreads;
	}
	
	public void setNumThreads(int numThreads) {
		this.numThreads = numThreads;
	}
	
	@Input
	public boolean isSaferBytecodeProvider() {
		return saferBytecodeProvider;
	}
	
	public void setSaferBytecodeProvider(boolean saferBytecodeProvider) {
		this.saferBytecodeProvider = saferBytecodeProvider;
	}
	
	@Input
	public boolean isSkipDecompile() {
		return skipDecompile;
	}
	
	public void setSkipDecompile(boolean skipDecompile) {
		this.skipDecompile = skipDecompile;
	}
	
	@Input
	public boolean isLinemapDebug() {
		return linemapDebug;
	}
	
	public void setLinemapDebug(boolean linemapDebug) {
		this.linemapDebug = linemapDebug;
	}
}
