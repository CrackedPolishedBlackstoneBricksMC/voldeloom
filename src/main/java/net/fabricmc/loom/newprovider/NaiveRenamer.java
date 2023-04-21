package net.fabricmc.loom.newprovider;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.mcp.Members;
import net.fabricmc.loom.mcp.NaiveAsmSrgRenamer;
import net.fabricmc.loom.mcp.NaiveTextualSrgRenamer;
import net.fabricmc.loom.util.ZipUtil;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class NaiveRenamer extends NewProvider<NaiveRenamer> {
	public NaiveRenamer(Project project, LoomGradleExtension extension) {
		super(project, extension);
	}
	
	//input
	private Path input;
	private String outputDirectory, outputFilename;
	private Members fields, methods;
	
	public NaiveRenamer input(Path input) {
		this.input = input;
		return this;
	}
	
	public NaiveRenamer outputFilename(String outputDirectory, String outputFilename) {
		this.outputDirectory = outputDirectory;
		this.outputFilename = outputFilename;
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
	
	//output
	private Path output;
	
	public Path getOutput() {
		return output;
	}
	
	public NaiveRenamer rename() throws Exception {
		//kludge: putting it next to the output of RemapperMcp
		output = getOrCreate(getCacheDir().resolve("mapped").resolve(outputDirectory).resolve(props.subst(outputFilename)), dest -> {
			Files.createDirectories(dest.getParent());
			doIt(input, dest, log, fields, methods);
		});
		
		return this;
	}
	
	public static void doIt(Path input, Path output, Logger log, Members fields, Members methods) throws Exception {
		log.warn("NaiveRenamer.doIt; input: {}, output: {}", input, output);
		
		try(FileSystem srcFs = ZipUtil.openFs(input); FileSystem dstFs = ZipUtil.createFs(output)) {
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
	}
}
