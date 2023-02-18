package net.fabricmc.loom;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ForgeCapabilities {
	public ForgeCapabilities(Project project, LoomGradleExtension extension) {
		this.project = project;
		this.extension = extension;
		this.log = project.getLogger();
	}
	
	private final Project project;
	private final LoomGradleExtension extension;
	private final Logger log;
	
	/**
	 * What naming scheme that this Forge version expects to find mods in.
	 * <p>
	 * By default, this evaluates to 'intermediary' since 1.5, and 'official' before it.
	 */
	public String distributionNamingScheme = "auto";
	
	/**
	 * If a field/method is missing a mapping, if 'true' the proguarded name will show through, and if 'false' the MCP name will.
	 * <p>
	 * By default, this evaluates to 'true' since 1.5, and 'false' before it.
	 * TODO: is this even a good idea?
	 */
	public Object srgsAsFallback = "auto";
	
	/**
	 * Minecraft used to contain shaded copies of libraries that MCP tried to map back to reality.
	 * The Forge installation procedure would sometimes delete these libraries after deobfuscating the game.
	 * The exact libraries changed over time.
	 * <p>
	 * Example: https://github.com/MinecraftForge/FML/blob/8e7956397dd80902f7ca69c466e833047dfa5010/build.xml#L295-L298
	 */
	public String classFilter = "auto";
	
	/**
	 * TODO: write javadoc when its not 4am
	 */
	public Object bouncycastleCheat = "auto";
	
	@SuppressWarnings("unused") //gradle api
	public String getDistributionNamingScheme() {
		return distributionNamingScheme;
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setDistributionNamingScheme(String distributionNamingScheme) {
		this.distributionNamingScheme = distributionNamingScheme;
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public Object getSrgsAsFallback() {
		return srgsAsFallback;
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setSrgsAsFallback(Object srgsAsFallback) {
		this.srgsAsFallback = srgsAsFallback;
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public String getClassFilter() {
		return classFilter;
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setClassFilter(String classFilter) {
		this.classFilter = classFilter;
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public Object getBouncycastleCheat() {
		return bouncycastleCheat;
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setBouncycastleCheat(Object bouncycastleCheat) {
		this.bouncycastleCheat = bouncycastleCheat;
		return this;
	}
	
	public String computeDistributionNamingScheme() {
		checkConfigured("computeDistributionNamingScheme");
		
		if("auto".equals(distributionNamingScheme)) {
			distributionNamingScheme = guessMinecraftMinorVersion() >= 5 ? Constants.INTERMEDIATE_NAMING_SCHEME : Constants.PROGUARDED_NAMING_SCHEME;
			log.info("|-> [ForgeCapabilities guess] I think this Forge version expects mods to be distributed in the '{}' namespace?", distributionNamingScheme);
		}
		
		return distributionNamingScheme;
	}
	
	public boolean useSrgsAsFallback() {
		checkConfigured("useSrgsAsFallback");
		
		if("auto".equals(srgsAsFallback)) {
			srgsAsFallback = guessMinecraftMinorVersion() >= 5;
			
			if((Boolean) srgsAsFallback) {
				log.info("|-> [ForgeCapabilities guess] I think this Forge version uses SRGs as the fallback when no mapping is defined?");
			} else {
				log.info("|-> [ForgeCapabilities guess] I think this Forge version uses proguarded names as the fallback when no mapping is defined?");
			}
		}
		
		if(srgsAsFallback instanceof String) return Boolean.parseBoolean((String) srgsAsFallback);
		if(srgsAsFallback instanceof Boolean) return (Boolean) srgsAsFallback;
		throw new IllegalStateException("Can't coerce " + srgsAsFallback.getClass() + " to a boolean, in computeSrgsAsFallback");
	}
	
	public Set<String> computeClassFilter() {
		checkConfigured("computeClassFilter");
		
		if("auto".equals(classFilter)) {
			int minor = guessMinecraftMinorVersion();
			
			if(minor <= 2) {
				log.info("|-> [ForgeCapabilities guess] I think this Forge version filters classes starting with 'argo' when remapping?");
				classFilter = "argo";
			} else if(minor <= 7) {
				log.info("|-> [ForgeCapabilities guess] I think this Forge version filters classes starting with 'argo' and 'org' when remapping?");
				classFilter = "argo;org";
			} else {
				//I don't really have any evidence that lib-deleting ended in 1.8 - TODO check.
				log.info("|-> [ForgeCapabilities guess] I don't think this Forge version filters any classes when remapping?");
				classFilter = "";
			}
		}
		
		return new HashSet<>(Arrays.asList(classFilter.split(";")));
	}
	
	public boolean computeBouncycastleCheat() {
		checkConfigured("computeBouncycastleCheat");
		
		if("auto".equals(bouncycastleCheat)) {
			bouncycastleCheat = guessMinecraftMinorVersion() == 3;
			
			if((Boolean) bouncycastleCheat) {
				log.info("|-> [ForgeCapabilities guess] Cheating and pretending bouncycastle is a Forge library");
			} else {
				log.info("|-> [ForgeCapabilities guess] Not cheating and pretending bouncycastle is a Forge libray");
			}
		}
		
		if(bouncycastleCheat instanceof String) return Boolean.parseBoolean((String) bouncycastleCheat);
		if(bouncycastleCheat instanceof Boolean) return (Boolean) bouncycastleCheat;
		throw new IllegalStateException("Can't coerce " + bouncycastleCheat.getClass() + " to a boolean, in computeBouncycastleCheat");
	}
	
	private void checkConfigured(String what) {
		if(!project.getState().getExecuted()) {
			throw new IllegalStateException("Accessing " + what + " before the project is evaluated means the user doesn't have a chance to configure it!");
		}
	}
	
	private int guessMinecraftMinorVersion() {
		String mcVersion = extension.getProviderGraph().mc.getVersion();
		
		try {
			return Integer.parseInt(mcVersion.split("\\.")[1]);
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			System.err.println("Couldn't guess the minor version of Minecraft version " + mcVersion);
			return 0;
		}
	}
}
