package net.fabricmc.loom.newprovider;

import com.google.common.base.Preconditions;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import net.fabricmc.loom.Constants;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.mcp.ForgeAccessTransformerSet;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Applies Forge's access transformers to a jar.
 * The access-transformed jar is accessible with {@code getTransformedJar()}.
 * <p>
 * Outside of development, this is normally done by Forge as it classloads Minecraft.
 */
public class AccessTransformer extends NewProvider<AccessTransformer> {
	public AccessTransformer(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	//inputs
	private ResolvedConfigElementWrapper forge;
	private Path forgeJarmodded;
	@Deprecated private String patchedVersionTag;
	
	public AccessTransformer forge(ResolvedConfigElementWrapper forge) {
		this.forge = forge;
		return this;
	}
	
	public AccessTransformer forgeJarmodded(Path forgeJarmodded) {
		this.forgeJarmodded = forgeJarmodded;
		return this;
	}
	
	@Deprecated public AccessTransformer patchedVersionTag(String patchedVersionTag) {
		this.patchedVersionTag = patchedVersionTag;
		return this;
	}
	
	//outputs
	private Set<Path> customAccessTransformers = new HashSet<>();
	private Path accessTransformedMc;
	
	public Path getTransformedJar() {
		return accessTransformedMc;
	}
	
	public AccessTransformer transform() throws Exception {
		Preconditions.checkNotNull(forge, "forge version");
		Preconditions.checkNotNull(forgeJarmodded, "jarmod");
		Preconditions.checkNotNull(patchedVersionTag, "patched version tag");
		
		projectmapped |= !getConfigurationByName(Constants.CUSTOM_ACCESS_TRANSFORMERS).resolve().isEmpty();
		
		customAccessTransformers = getConfigurationByName(Constants.CUSTOM_ACCESS_TRANSFORMERS)
			.resolve()
			.stream()
			.map(File::toPath)
			.collect(Collectors.toSet());
		
		String discriminator;
		if(customAccessTransformers.isEmpty()) {
			discriminator = "";
		} else {
			log.lifecycle("] Found {} custom access transformer files.", customAccessTransformers.size());
			discriminator = "-" + customAccessTransformerHash();
		}
		
		accessTransformedMc = getCacheDir().resolve("minecraft-" + patchedVersionTag + "-atd" + discriminator + ".jar");
		log.lifecycle("] access-transformed jar: {}", accessTransformedMc);
		cleanOnRefreshDependencies(accessTransformedMc);
		
		if(Files.notExists(accessTransformedMc)) {
			log.lifecycle("|-> Access-transformed jar does not exist, parsing Forge's access transformers...");
			
			//Read forge ats
			ForgeAccessTransformerSet ats = new ForgeAccessTransformerSet();
			try(FileSystem forgeFs = FileSystems.newFileSystem(URI.create("jar:" + forge.getPath().toUri()), Collections.emptyMap())) {
				//TODO: where do these names come from, can they be read from the jar?
				// 1.2.5 does not have these files
				for(String atFileName : Arrays.asList("forge_at.cfg", "fml_at.cfg")) {
					Path atFilePath = forgeFs.getPath(atFileName);
					if(Files.exists(atFilePath)) {
						log.info("\\-> Loading {}...", atFileName);
						ats.load(atFilePath);
					} else {
						log.info("\\-> No {} in the Forge jar.", atFileName);
					}
				}
			}
			
			log.info("\\-> Found {} access transformers affecting {} classes inside Forge.", ats.getCount(), ats.getTouchedClassCount());
			
			Set<File> customAtFiles = getConfigurationByName(Constants.CUSTOM_ACCESS_TRANSFORMERS).getFiles();
			if(!customAtFiles.isEmpty()) {
				log.info("|-> Loading {} custom access transformer file{}...", customAtFiles.size(), customAtFiles.size() == 1 ? "" : "s");
				
				for(File customAtFile : customAtFiles) {
					Path customAtPath = customAtFile.toPath();
					
					if(Files.exists(customAtPath)) {
						log.info("\\-> Loading {}...", customAtPath);
						ats.load(customAtPath);
					} else {
						log.warn("\\-> Custom AT at {} doesn't exist!", customAtPath);
					}
				}
				
				log.info("\\-> After incorporationg custom ATs, there are {} access transformers affecting {} classes.", ats.getCount(), ats.getTouchedClassCount());
			}
			
			log.info("|-> Performing transform...");
			
			try(
				FileSystem unAccessTransformedFs = FileSystems.newFileSystem(URI.create("jar:" + forgeJarmodded.toUri()), Collections.emptyMap());
				FileSystem accessTransformedFs = FileSystems.newFileSystem(URI.create("jar:" + accessTransformedMc.toUri()), Collections.singletonMap("create", "true"))) {
				Files.walkFileTree(unAccessTransformedFs.getPath("/"), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path srcPath, BasicFileAttributes attrs) throws IOException {
						Path dstPath = accessTransformedFs.getPath(srcPath.toString());
						Files.createDirectories(dstPath.getParent());
						
						if(srcPath.toString().endsWith(".class")) {
							//Kludgey, but means we know the class data without reading the file
							String className = srcPath.toString()
								.replace("\\", "/")     //maybe windows does this idk?
								.substring(1)           //leading slash
								.replace(".class", ""); //funny extension
							
							log.debug("Visiting class {}", className);
							
							if(ats.touchesClass(className)) {
								log.debug("There's an access transformer for {}", className);
								
								try(InputStream srcReader = new BufferedInputStream((Files.newInputStream(srcPath)))) {
									ClassReader srcClassReader = new ClassReader(srcReader);
									ClassWriter dstClassWriter = new ClassWriter(0);
									srcClassReader.accept(ats.new AccessTransformingClassVisitor(dstClassWriter), 0);
									Files.write(dstPath, dstClassWriter.toByteArray());
								}
								
								return FileVisitResult.CONTINUE;
							}
						}
						
						log.debug("Copying {} without changing it (not a class/no AT for it)", srcPath);
						
						Files.copy(srcPath, dstPath);
						return FileVisitResult.CONTINUE;
					}
				});
			}
			
			log.info("|-> Access transformation success! :)");
			
			List<String> unusedAtsReport = ats.reportUnusedTransformers();
			if(!unusedAtsReport.isEmpty()) {
				log.warn("|-> Found {} unused access transformers.", unusedAtsReport.size());
				unusedAtsReport.forEach(log::warn);
			}
		}
		
		return this;
	}
	
	@SuppressWarnings("UnstableApiUsage")
	private String customAccessTransformerHash() throws Exception {
		Hasher digest = Hashing.sha256().newHasher();
		for(Path path : customAccessTransformers) {
			digest.putBytes(Files.readAllBytes(path));
			digest.putByte((byte) 0);
		}
		
		String digestString = String.format("%040x", new BigInteger(1, digest.hash().asBytes()));
		return digestString.substring(0, 8);
	}
}
