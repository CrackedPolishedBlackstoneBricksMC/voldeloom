package net.fabricmc.loom;

import org.gradle.api.Project;

public class ForgeCapabilities {
	public ForgeCapabilities(Project project, LoomGradleExtension extension) {
		this.project = project;
		this.extension = extension;
	}
	
	private final Project project;
	private final LoomGradleExtension extension;
	
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
	
	public String computeDistributionNamingScheme() {
		if(!project.getState().getExecuted()) {
			throw new IllegalStateException("Accessing computeDistributionNamingScheme before the project is evaluated means the user doesn't have a chance to configure it");
		}
		
		if("auto".equals(distributionNamingScheme)) {
			distributionNamingScheme = guessMinecraftMinorVersion(extension.getProviderGraph().mc.getVersion()) >= 5 ? Constants.INTERMEDIATE_NAMING_SCHEME : Constants.PROGUARDED_NAMING_SCHEME;
			project.getLogger().info("|-> [ForgeCapabilities guess] I think this Forge version expects mods to be distributed in the '{}' namespace?", distributionNamingScheme);
		}
		
		return distributionNamingScheme;
	}
	
	public boolean useSrgsAsFallback() {
		if(!project.getState().getExecuted()) {
			throw new IllegalStateException("Accessing useSrgsAsFallback before the project is evaluated means the user didn't have a chance to configure it");
		}
		
		if(srgsAsFallback instanceof String) {
			if("auto".equals(srgsAsFallback)) {
				srgsAsFallback = guessMinecraftMinorVersion(extension.getProviderGraph().mc.getVersion()) >= 5;
				
				if((Boolean) srgsAsFallback) {
					project.getLogger().info("|-> [ForgeCapabilities guess] I think this Forge version uses SRGs as the fallback when no mapping is defined?");
				} else {
					project.getLogger().info("|-> [ForgeCapabilities guess] I think this Forge version uses proguarded names as the fallback when no mapping is defined?");
				}
			} else return Boolean.parseBoolean((String) srgsAsFallback);
		}
		
		if(srgsAsFallback instanceof Boolean) return ((Boolean) srgsAsFallback);
		
		throw new IllegalStateException("Can't coerce " + srgsAsFallback.getClass() + " to a boolean, in computeSrgsAsFallback");
	}
	
	private int guessMinecraftMinorVersion(String mcVersion) {
		try {
			return Integer.parseInt(mcVersion.split("\\.")[1]);
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			System.err.println("Couldn't guess the minor version of Minecraft version " + mcVersion);
			return 0;
		}
	}
}
