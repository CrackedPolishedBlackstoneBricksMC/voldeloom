package net.fabricmc.loom;

import net.fabricmc.loom.util.OperatingSystem;
import net.fabricmc.loom.util.Suppliers;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
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
	
	/**
	 * What naming scheme that this Forge version expects to find mods in.
	 * <p>
	 * By default, this evaluates to 'intermediary' since 1.5, and 'official' before it.
	 */
	public Supplier<String> distributionNamingScheme = Suppliers.memoize(this::guessDistributionNamingScheme);
	
	public String guessDistributionNamingScheme() {
		checkConfigured("guessDistributionNamingScheme");
		
		String dist = guessMinecraftMinorVersion() >= 5 ? Constants.INTERMEDIATE_NAMING_SCHEME : Constants.PROGUARDED_NAMING_SCHEME;
		log.info("|-> [ForgeCapabilities guess] I think this Forge version expects mods to be distributed in the '{}' namespace?", dist);
		return dist;
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setDistributionNamingScheme(String distributionNamingScheme) {
		this.distributionNamingScheme = () -> distributionNamingScheme;
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setDistributionNamingSchemeSupplier(Supplier<String> distributionNamingScheme) {
		this.distributionNamingScheme = distributionNamingScheme;
		return this;
	}
	
	/**
	 * If a field/method is missing a mapping, if 'true' the proguarded name will show through, and if 'false' the MCP name will.
	 * <p>
	 * By default, this evaluates to 'true' since 1.5, and 'false' before it.
	 * TODO: is this even a good idea?
	 */
	public Supplier<Boolean> srgsAsFallback = Suppliers.memoize(this::guessSrgsAsFallback);
	
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
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setSrgsAsFallback(boolean srgsAsFallback) {
		this.srgsAsFallback = () -> srgsAsFallback;
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setSrgsAsFallbackSupplier(Supplier<Boolean> srgsAsFallback) {
		this.srgsAsFallback = srgsAsFallback;
		return this;
	}
	
	/**
	 * Minecraft used to contain shaded copies of libraries that MCP tried to map back to reality.
	 * The Forge installation procedure would sometimes delete these libraries after deobfuscating the game.
	 * The exact libraries changed over time.
	 * <p>
	 * Example: https://github.com/MinecraftForge/FML/blob/8e7956397dd80902f7ca69c466e833047dfa5010/build.xml#L295-L298
	 */
	public Supplier<Set<String>> classFilter = Suppliers.memoize(this::guessClassFilter);
	
	public Set<String> guessClassFilter() {
		checkConfigured("guessClassFilter");
		
		int minor = guessMinecraftMinorVersion();
		
		if(minor <= 2) {
			//ODD: the Ant scripts from this era did have an argo filter, but in practice the game noclassdefs with an argo filter
			log.info("|-> [ForgeCapabilities guess] I don't think this Forge version filters any classes when remapping?");
			return Collections.emptySet();
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
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setClassFilter(Set<String> classFilter) {
		this.classFilter = () -> classFilter;
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setClassFilterSupplier(Supplier<Set<String>> classFilter) {
		this.classFilter = classFilter;
		return this;
	}
	
	/**
	 * TODO: write javadoc when its not 4am
	 */
	public Supplier<Boolean> bouncycastleCheat = Suppliers.memoize(this::guessBouncycastleCheat);
	
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
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setBouncycastleCheat(boolean bouncycastleCheat) {
		this.bouncycastleCheat = () -> bouncycastleCheat;
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setBouncycastleCheatSupplier(Supplier<Boolean> bouncycastleCheat) {
		this.bouncycastleCheat = bouncycastleCheat;
		return this;
	}
	
	public Supplier<Boolean> requiresLaunchwrapper = Suppliers.memoize(this::guessRequiresLaunchwrapper);
	
	public boolean guessRequiresLaunchwrapper() {
		checkConfigured("guessSupportsAssetIndex");
		
		if(guessMinecraftMinorVersion() >= 6) {
			log.info("|-> [ForgeCapabilities guess] Guessing that this Forge version requires a Launchwrapper tweaker?");
			return true;
		} else {
			log.info("|-> [ForgeCapabilities guess] Guessing that this Forge version is launched directly from the normal Minecraft main class?");
			return false;
		}
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setRequiresLaunchwrapper(boolean requiresLaunchwrapper) {
		this.requiresLaunchwrapper = () -> requiresLaunchwrapper;
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setRequiresLaunchwrapperSupplier(Supplier<Boolean> requiresLaunchwrapper) {
		this.requiresLaunchwrapper = requiresLaunchwrapper;
		return this;
	}
	
	public enum LibraryDownloader {
		NONE,
		DEAD,
		CONFIGURABLE
	}
	public Supplier<LibraryDownloader> libraryDownloaderType = Suppliers.memoize(this::guessLibraryDownloaderType);
	
	public LibraryDownloader guessLibraryDownloaderType() {
		checkConfigured("guessLibraryDownloaderType");
		
		int minor = guessMinecraftMinorVersion();
		if(minor == 5) {
			log.info("|-> [ForgeCapabilities guess] Guessing that this Forge version has a library downloader configurable through fml.core.libraries.mirror?");
			return LibraryDownloader.CONFIGURABLE;
		} else if(minor < 5) {
			log.info("|-> [ForgeCapabilities guess] Guessing that this Forge version has a broken library downloader that contacts a dead server?");
			return LibraryDownloader.DEAD;
		} else {
			log.info("|-> [ForgeCapabilities guess] Guessing that this Forge version does not have a library downloader?");
			return LibraryDownloader.NONE;
		}
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setLibraryDownloaderType(LibraryDownloader libraryDownloaderType) {
		this.libraryDownloaderType = () -> libraryDownloaderType;
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities setLibraryDownloaderTypeSupplier(Supplier<LibraryDownloader> libraryDownloaderType) {
		this.libraryDownloaderType = libraryDownloaderType;
		return this;
	}
	
	public Supplier<Boolean> supportsAssetsDir = Suppliers.memoize(this::guessSupportsAssetsDir);
	
	public boolean guessSupportsAssetsDir() {
		checkConfigured("guessSupportsAssetIndex");
		
		if(guessMinecraftMinorVersion() >= 6) {
			log.info("|-> [ForgeCapabilities guess] Guessing that this Minecraft version supports setting --assetsDir?");
			return true;
		} else {
			log.info("|-> [ForgeCapabilities guess] Guessing that this Minecraft version does not support setting --assetsDir?");
			return false;
		}
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities supportsAssetsDir(boolean supportsAssetsDir) {
		this.supportsAssetsDir = () -> supportsAssetsDir;
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities supportsAssetsDirSupplier(Supplier<Boolean> supportsAssetsDir) {
		this.supportsAssetsDir = supportsAssetsDir;
		return this;
	}
	
	public Supplier<Boolean> mappedAccessTransformers = Suppliers.memoize(this::guessMappedAccessTransformers);
	
	public boolean guessMappedAccessTransformers() {
		checkConfigured("guessMappedAccessTransformers");
		
		if(guessMinecraftMinorVersion() >= 7) {
			log.info("|-> [ForgeCapabilities guess] Guessing that this Forge version had '{}'-named access transformers?", Constants.INTERMEDIATE_NAMING_SCHEME);
			return true;
		} else {
			log.info("|-> [ForgeCapabilities guess] Guessing that this Forge version had '{}'-named access transformers?", Constants.PROGUARDED_NAMING_SCHEME);
			return false;
		}
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities mappedAccessTransformers(boolean mappedAccessTransformers) {
		this.mappedAccessTransformers = () -> mappedAccessTransformers;
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities mappedAccessTransformersSupplier(Supplier<Boolean> mappedAccessTransformers) {
		this.mappedAccessTransformers = mappedAccessTransformers;
		return this;
	}
	
	public Supplier<Function<Path, Path>> minecraftRealPath = Suppliers.memoize(this::guessMinecraftRealPath);
	
	public Function<Path, Path> guessMinecraftRealPath() {
		checkConfigured("guessMinecraftRealPath");
		
		if(guessMinecraftMinorVersion() <= 2) {
			if(OperatingSystem.getOS().contains("osx")) {
				log.info("|-> [ForgeCapabilities guess] Guessing that this Minecraft version appends 'minecraft' to the user-specified path (without a dot character, because this is a Mac)");
				return p -> p.resolve("minecraft");
			} else {
				log.info("|-> [ForgeCapabilities guess] Guessing that this Minecraft version appends '.minecraft' to the user-specified run dir");
				return p -> p.resolve(".minecraft");
			}
		} else {
			log.info("|-> [ForgeCapabilities guess] Guessing that this Minecraft version leaves the run dir unchanged");
			return Function.identity();
		}
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities minecraftRealPath(Function<Path, Path> minecraftRealPath) {
		this.minecraftRealPath = () -> minecraftRealPath;
		return this;
	}
	
	@SuppressWarnings("unused") //gradle api
	public ForgeCapabilities minecraftRealPathSupplier(Supplier<Function<Path, Path>> minecraftRealPath) {
		this.minecraftRealPath = minecraftRealPath;
		return this;
	}
}
