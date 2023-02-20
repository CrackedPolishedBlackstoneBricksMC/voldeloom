package net.fabricmc.loom;

import com.google.common.base.Suppliers;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

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
	private Supplier<String> distributionNamingScheme = Suppliers.memoize(this::guessDistributionNamingScheme);
	
	/**
	 * If a field/method is missing a mapping, if 'true' the proguarded name will show through, and if 'false' the MCP name will.
	 * <p>
	 * By default, this evaluates to 'true' since 1.5, and 'false' before it.
	 * TODO: is this even a good idea?
	 */
	private Supplier<Boolean> srgsAsFallback = Suppliers.memoize(this::guessSrgsAsFallback);
	
	/**
	 * Minecraft used to contain shaded copies of libraries that MCP tried to map back to reality.
	 * The Forge installation procedure would sometimes delete these libraries after deobfuscating the game.
	 * The exact libraries changed over time.
	 * <p>
	 * Example: https://github.com/MinecraftForge/FML/blob/8e7956397dd80902f7ca69c466e833047dfa5010/build.xml#L295-L298
	 */
	private Supplier<Set<String>> classFilter = Suppliers.memoize(this::guessClassFilter);
	
	/**
	 * TODO: write javadoc when its not 4am
	 */
	private Supplier<Boolean> bouncycastleCheat = Suppliers.memoize(this::guessBouncycastleCheat);
	
	public String getDistributionNamingScheme() {
		return distributionNamingScheme.get();
	}
	
	public boolean getSrgsAsFallback() {
		return srgsAsFallback.get();
	}
	
	public Set<String> getClassFilter() {
		return classFilter.get();
	}
	
	public boolean getBouncycastleCheat() {
		return bouncycastleCheat.get();
	}
	
	public String guessDistributionNamingScheme() {
		checkConfigured("guessDistributionNamingScheme");
		
		String dist = guessMinecraftMinorVersion() >= 5 ? Constants.INTERMEDIATE_NAMING_SCHEME : Constants.PROGUARDED_NAMING_SCHEME;
		log.info("|-> [ForgeCapabilities guess] I think this Forge version expects mods to be distributed in the '{}' namespace?", dist);
		return dist;
	}
	
	public boolean guessSrgsAsFallback() {
		checkConfigured("guessSrgsAsFallback");
		
		if(guessMinecraftMinorVersion() >= 5) {
			log.info("|-> [ForgeCapabilities guess] I think this Forge version uses SRGs as the fallback when no mapping is defined?");
			return true;
		} else {
			log.info("|-> [ForgeCapabilities guess] I think this Forge version uses proguarded names as the fallback when no mapping is defined?");
			return false;
		}
	}
	
	public Set<String> guessClassFilter() {
		checkConfigured("guessClassFilter");
		
		int minor = guessMinecraftMinorVersion();
		
		if(minor <= 2) {
			log.info("|-> [ForgeCapabilities guess] I think this Forge version filters classes starting with 'argo' when remapping?");
			return Collections.singleton("argo");
		} else if(minor <= 7) {
			log.info("|-> [ForgeCapabilities guess] I think this Forge version filters classes starting with 'argo' and 'org' when remapping?");
			
			HashSet<String> lol = new HashSet<>(2);
			lol.add("argo");
			lol.add("org");
			return lol;
		} else {
			//I don't really have any evidence that lib-deleting ended in 1.8 - TODO check.
			log.info("|-> [ForgeCapabilities guess] I don't think this Forge version filters any classes when remapping?");
			return Collections.emptySet();
		}
	}
	
	public boolean guessBouncycastleCheat() {
		checkConfigured("guessBouncycastleCheat");
		
		if(guessMinecraftMinorVersion() == 3) {
			log.info("|-> [ForgeCapabilities guess] Cheating and pretending bouncycastle is a Forge library");
			return true;
		} else {
			log.info("|-> [ForgeCapabilities guess] Not cheating and pretending bouncycastle is a Forge libray");
			return false;
		}
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
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setDistributionNamingScheme(String distributionNamingScheme) {
		this.distributionNamingScheme = () -> distributionNamingScheme;
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setSrgsAsFallback(boolean srgsAsFallback) {
		this.srgsAsFallback = () -> srgsAsFallback;
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setClassFilter(Set<String> classFilter) {
		this.classFilter = () -> classFilter;
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setBouncycastleCheat(boolean bouncycastleCheat) {
		this.bouncycastleCheat = () -> bouncycastleCheat;
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setDistributionNamingSchemeSupplier(Supplier<String> distributionNamingScheme) {
		this.distributionNamingScheme = distributionNamingScheme;
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setSrgsAsFallbackSupplier(Supplier<Boolean> srgsAsFallback) {
		this.srgsAsFallback = srgsAsFallback;
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setClassFilterSupplier(Supplier<Set<String>> classFilter) {
		this.classFilter = classFilter;
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setBouncycastleCheatSupplier(Supplier<Boolean> bouncycastleCheat) {
		this.bouncycastleCheat = bouncycastleCheat;
		return this;
	}
}
