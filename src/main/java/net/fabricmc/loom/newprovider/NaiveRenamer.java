package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.mcp.McpMappings;
import net.fabricmc.loom.mcp.Members;
import net.fabricmc.loom.mcp.NaiveAsmSrgRenamer;
import net.fabricmc.loom.mcp.NaiveTextualSrgRenamer;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;

public class NaiveRenamer extends NewProvider<NaiveRenamer> {
	public NaiveRenamer(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	private Path input, output;
	private Members fields, methods;
	
	public NaiveRenamer input(Path input) {
		this.input = input;
		return this;
	}
	
	public NaiveRenamer output(Path output) {
		this.output = output;
		return this;
	}
	
	public NaiveRenamer fields(Members fields) {
		this.fields = fields;
		return this;
	}
	
	public NaiveRenamer methods(Members methods) {
		this.methods = methods;
		return this;
	}
	
	public NaiveRenamer mappings(McpMappings mappings) {
		return fields(mappings.fields).methods(mappings.methods);
	}
	
	public Path getOutput() {
		return output;
	}
	
	public NaiveRenamer doIt() throws Exception {
		log.info("] naive renamer input: {}", input);
		log.info("] naive renamer output: {}", output);
		
		cleanOnRefreshDependencies(output);
		
		if(Files.exists(output)) return this;
		
		log.info("|-> Performing naive renaming...");
		Files.createDirectories(output.getParent());
		
		try(FileSystem srcFs = FileSystems.newFileSystem(URI.create("jar:" + input.toUri()), Collections.emptyMap());
		    FileSystem dstFs = FileSystems.newFileSystem(URI.create("jar:" + output.toUri()), Collections.singletonMap("create", "true"))) {
			Files.walkFileTree(srcFs.getPath("/"), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path srcDir, BasicFileAttributes attrs) throws IOException {
					Files.createDirectories(dstFs.getPath(srcDir.toString()));
					return FileVisitResult.CONTINUE;
				}
				
				@Override
				public FileVisitResult visitFile(Path srcPath, BasicFileAttributes attrs) throws IOException {
					Path dstPath = dstFs.getPath(srcPath.toString());
					String dstPathString = dstPath.toString();
					
					if(dstPathString.endsWith(".class")) {
						try(InputStream srcReader = new BufferedInputStream(Files.newInputStream(srcPath))) {
							ClassReader srcClassReader = new ClassReader(srcReader);
							ClassWriter dstClassWriter = new ClassWriter(0);
							srcClassReader.accept(new NaiveAsmSrgRenamer(dstClassWriter, fields, methods), 0);
							Files.write(dstPath, dstClassWriter.toByteArray());
						}
					} else if(dstPathString.endsWith(".java")) {
						String src = new String(Files.readAllBytes(srcPath), StandardCharsets.UTF_8);
						String dst = new NaiveTextualSrgRenamer(fields, methods).rename(src);
						Files.write(dstPath, dst.getBytes(StandardCharsets.UTF_8));
					} else {
						Files.copy(srcPath, dstPath);
					}
					
					return FileVisitResult.CONTINUE;
				}
			});
		}
		
		log.info("|-> Done.");
		
		return this;
	}
}
