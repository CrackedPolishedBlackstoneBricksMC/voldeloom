/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.util;

import com.google.common.base.Preconditions;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TinyRemapperSession {
	public TinyRemapperSession() {}
	
	private TinyTree mappings = null;
	private Path inputJar = null;
	private String inputNamingScheme = null;
	private Collection<Path> inputClasspath = null;
	private final Map<String, Path> outputJarsByNamingScheme = new HashMap<>();
	private Consumer<String> logger = s -> {};
	
	public TinyRemapperSession setMappings(TinyTree mappings) {
		this.mappings = mappings;
		return this;
	}
	
	public TinyRemapperSession setInputJar(Path inputJar) {
		this.inputJar = inputJar;
		return this;
	}
	
	public TinyRemapperSession addOutputJar(String namingScheme, Path outputJar) {
		outputJarsByNamingScheme.put(namingScheme, outputJar);
		return this;
	}
	
	public TinyRemapperSession setInputNamingScheme(String inputNamingScheme) {
		this.inputNamingScheme = inputNamingScheme;
		return this;
	}
	
	public TinyRemapperSession setInputClasspath(Collection<Path> inputClasspath) {
		this.inputClasspath = inputClasspath;
		return this;
	}
	
	public TinyRemapperSession setLogger(Consumer<String> logger) {
		this.logger = logger;
		return this;
	}
	
	public void run() throws IOException {
		Preconditions.checkNotNull(mappings, "mappings");
		Preconditions.checkNotNull(inputJar, "inputJar");
		Preconditions.checkNotNull(inputNamingScheme, "inputNamingScheme");
		Preconditions.checkNotNull(inputClasspath, "inputClasspath");
		Preconditions.checkNotNull(logger, "logger");
		Preconditions.checkState(!outputJarsByNamingScheme.isEmpty(), "outputJarsByNamingScheme has something to do");
		
		logger.accept("\\-> beginning remap of '" + inputNamingScheme + "'-named jar at " + inputJar);
		for(String outputNamingScheme : outputJarsByNamingScheme.keySet()) {
			Path outputJar = outputJarsByNamingScheme.get(outputNamingScheme);
			
			logger.accept("  \\-> remapping to '" + outputNamingScheme + "' names, and saving to " + outputJar);
			
			TinyRemapper remapper = TinyRemapper.newRemapper()
				.withMappings(TinyRemapperMappingsHelper.create(mappings, inputNamingScheme, outputNamingScheme, true))
				.renameInvalidLocals(true)
				.rebuildSourceFilenames(true)
				.build();
			
			//TODO where does do the argo come from???
			// This used to be handled by CursedLibDeleterProcessor
			// I've tracked it down to be coming from tiny-remapper, no argo before this step
			// Unfortunately ! Debugger is broken in many places in tiny-remapper,
			// probably related to it being multithreaded weirdness
			Predicate<String> filterWtf = s -> !s.startsWith("argo") && !s.startsWith("org");
			
			try(OutputConsumerPath oc = new OutputConsumerPath.Builder(outputJar).filter(filterWtf).build()) {
				oc.addNonClassFiles(inputJar);
				remapper.readClassPath(inputClasspath.toArray(new Path[0]));
				remapper.readInputs(inputJar);
				remapper.apply(oc);
			} catch (Exception e) {
				throw new RuntimeException("Failure during remapping session while trying to map to " + outputNamingScheme + " (" + this + ")", e);
			} finally {
				remapper.finish();
			}
			
			logger.accept("\\-> done :)");
		}
	}
	
	@Override
	public String toString() {
		StringBuilder out = new StringBuilder();
		out.append("TinyRemapperSession { mappings = ");
		out.append(mappings);
		out.append(", inputJar = ");
		out.append(inputJar);
		out.append(", inputNamingScheme = ");
		out.append(inputNamingScheme);
		out.append(", inputClasspath = ");
		out.append(inputClasspath.stream().map(Path::toString).collect(Collectors.joining(", ")));
		out.append("; ");
		for(String outputNamingScheme : outputJarsByNamingScheme.keySet()) {
			Path outputJar = outputJarsByNamingScheme.get(outputNamingScheme);
			out.append("outputJar named with ");
			out.append(outputNamingScheme);
			out.append(" at ");
			out.append(outputJar);
		}
		out.append(" }");
		return out.toString();
	}
}
