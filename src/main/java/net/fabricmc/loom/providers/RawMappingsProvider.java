package net.fabricmc.loom.providers;

import net.fabricmc.loom.Constants;
import net.fabricmc.loom.DependencyProvider;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.mcp.Members;
import net.fabricmc.loom.util.mcp.Packages;
import net.fabricmc.loom.util.mcp.Srg;
import org.gradle.api.Project;

import javax.inject.Inject;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/**
 * Parses mappings in-memory.
 *
 * Supported formats are currently:
 * <ul>
 *   <li>MCP 1.4.7 zips, which do not include package information</li>
 *   <li>a zip of the Minecraft Forge source code, which does include package information</li>
 * </ul>
 * 
 * @see TinyMappingsProvider for what writes the actual tinyfiles
 */
public class RawMappingsProvider extends DependencyProvider {
	@Inject
	public RawMappingsProvider(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private Path rawMappingsJar;
	private String mappingsDepString;
	private String mappingDiscriminant = "";
	
	//TODO: It's Bad!
	
	private Srg joined;
	private Packages packages;
	private Members fields;
	private Members methods;
	
	private boolean alreadyTinyv2 = false;
	
	@Override
	protected void performSetup() throws Exception {
		DependencyInfo mappingsDependency = getSingleDependency(Constants.MAPPINGS);
		rawMappingsJar = mappingsDependency.resolveSinglePath();
		
		//TODO: REMOVE this hack
		if(extension.forgeCapabilities.useSrgsAsFallback()) mappingDiscriminant += "-srgfallback";
		
		//outputs
		mappingsDepString = mappingsDependency.getDepString() + mappingDiscriminant;
		
		project.getLogger().lifecycle("] mappings dep: {}", mappingsDepString);
		project.getLogger().lifecycle("] mappings source: {}", rawMappingsJar);
		
		try(FileSystem mcpZipFs = FileSystems.newFileSystem(URI.create("jar:" + rawMappingsJar.toUri()), Collections.emptyMap())) {
			//TODO: Remove this crap when i do the good mappings system
			Path tinyv2FunnyMoments = mcpZipFs.getPath("mappings/mappings.tiny");
			if(Files.exists(tinyv2FunnyMoments)) {
				//WOW its already in tinyv2 format how neat!!!
				project.getLogger().warn("MAPPINGS ALREADY TINYv2 I THINK!!!!! Fyi it should probably contain {} {} {} headers", Constants.PROGUARDED_NAMING_SCHEME, Constants.INTERMEDIATE_NAMING_SCHEME, Constants.MAPPED_NAMING_SCHEME);
				alreadyTinyv2 = true;
			} else {
				
				Path conf;
				if(Files.exists(mcpZipFs.getPath("forge/fml/conf"))) {
					conf = mcpZipFs.getPath("forge/fml/conf"); //Forge 1.3 to Forge 1.7
				} else if(Files.exists(mcpZipFs.getPath("forge/conf"))) {
					conf = mcpZipFs.getPath("forge/conf"); //Forge 1.2
				} else if(Files.exists(mcpZipFs.getPath("conf"))) {
					conf = mcpZipFs.getPath("conf"); //MCP
				} else {
					conf = mcpZipFs.getPath(""); //manually zipped mappings?
				}
				project.getLogger().lifecycle("] Mappings root detected to be '{}'", conf);
				
				project.getLogger().lifecycle("|-> Reading joined.srg...");
				if(Files.exists(conf.resolve("joined.srg"))) {
					joined = new Srg().read(conf.resolve("joined.srg"));
				} else {
					//just assume we're manually merging a client and server srg
					//TODO: newids?
					project.getLogger().lifecycle("\\-> No joined.srg exists. Reading client.srg...");
					Srg client = new Srg().read(conf.resolve("client.srg"));
					
					project.getLogger().lifecycle("\\-> Reading server.srg...");
					Srg server = new Srg().read(conf.resolve("server.srg"));
					
					project.getLogger().lifecycle("\\-> Manually joining srgs...");
					joined = client.mergeWith(server);
				}
				
				//TODO YEET this into the stratosphere
				for(String deleteThis : extension.hackHackHackDontMapTheseClasses) {
					joined.unmapClass(deleteThis);
				}
				
				project.getLogger().lifecycle("|-> Reading fields.csv...");
				fields = new Members().read(conf.resolve("fields.csv"));
				
				project.getLogger().lifecycle("|-> Reading methods.csv...");
				methods = new Members().read(conf.resolve("methods.csv"));
				
				project.getLogger().lifecycle("|-> Reading packages.csv...");
				if(Files.exists(conf.resolve("packages.csv"))) {
					packages = new Packages().read(conf.resolve("packages.csv"));
				} else {
					project.getLogger().lifecycle("\\-> No packages.csv exists.");
					packages = null;
				}
				
				project.getLogger().lifecycle("|-> Done!");
			}
		}
	}
	
	public Path getRawMappingsJar() {
		return rawMappingsJar;
	}
	
	public String getMappingsDepString() {
		return mappingsDepString;
	}
	
	public String getMappingDiscriminant() {
		return mappingDiscriminant;
	}
	
	public Srg getJoined() {
		return joined;
	}
	
	public Packages getPackages() {
		return packages;
	}
	
	public Members getFields() {
		return fields;
	}
	
	public Members getMethods() {
		return methods;
	}
	
	public boolean isAlreadyTinyv2() {
		return alreadyTinyv2;
	}
}