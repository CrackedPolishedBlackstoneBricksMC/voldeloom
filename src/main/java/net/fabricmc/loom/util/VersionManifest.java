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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * A per-version Minecraft version manifest.
 * <p>
 * This class is intended to be deserialized with Google GSON.
 */
@SuppressWarnings("unused")
public class VersionManifest {
	public static VersionManifest read(Path path) throws IOException {
		try(BufferedReader reader = Files.newBufferedReader(path)) {
			return new Gson().fromJson(reader, VersionManifest.class);
		}
	}
	
	public List<Library> libraries;
	public Map<String, Downloads> downloads;
	@SerializedName("assetIndex") public AssetIndexReference assetIndexReference;
	public String id; //version number
	public String mainClass;
	public String minecraftArguments;

	public static class Downloads {
		public String url;
		public String sha1;
	}
	
	public static class AssetIndexReference {
		public String id;
		public String sha1;
		public String url;
	}
	
	//NON-NATIVE DEPS (eg. joptsimple)
	// specify "name", specify "downloads">"artifact"
	// an artifact (path/sha/size/url) is under "downloads">"artifact"
	//
	//NON-NATIVE SUPPORT LIBS FOR NATIVE DEPS (eg. lwjgl_util)
	// same but specify "rules" as well
	//
	//NATIVE DEPS (eg. jinput)
	// specify "name", "downloads">"classifiers", "natives", and "extract"
	// "natives" is a map from platform to classifier
	// look up the classifier for that platform, then look in downloads/classifiers to get the artifact
	// you may need to subst ${arch} for 32 or 64 in the classifier ^^
	// "extract" is rules for extracting the native library, which is typically "skip META-INF" (voldeloom does not parse these)

	public static class LibraryDownloads {
		@SerializedName("artifact")
		public LibraryArtifact mainArtifact;
		
		@SerializedName("classifiers")
		public Map<String, LibraryArtifact> classifiedArtifacts;
	}
	
	public static class LibraryArtifact {
		public String path, sha1, url;
		public int size;
		
		//Mojang suggests a directory structure in "path", but I'd rather just resolve into a flat dir
		public Path resolveFlat(Path root) {
			int slash = path.lastIndexOf('/');
			String justFilename = slash == -1 ? path : path.substring(slash + 1);
			return root.resolve(justFilename);
		}
	}
	
	public static class Library {
		public String name;
		public @Nullable JsonObject natives;
		public LibraryDownloads downloads;
		public Rule[] rules;
		
		//Used by Forge 1.6/1.7's internal version.json. Not vanilla.
		@SerializedName("url") public String forgeDownloadRoot;
		
		public boolean isCustomForge() {
			return forgeDownloadRoot != null;
		}
		
		public boolean allowed() {
			//no rules -> always allowed
			if(rules == null || rules.length == 0) return true;
			
			//TODO copypaste from last time, logic is weird
			boolean success = false;
			
			for (Rule rule : this.rules) {
				if (rule.os != null && rule.os.name != null) {
					if (rule.os.name.equalsIgnoreCase(OperatingSystem.getOS())) {
						return rule.action.equalsIgnoreCase("allow");
					}
				} else {
					success = rule.action.equalsIgnoreCase("allow");
				}
			}
			
			return success;
		}
		
		public String getMavenCoordinate() {
			return name;
		}
		
		public boolean hasNatives() {
			return natives != null && natives.size() > 0;
		}
		
		public LibraryArtifact nativeArtifactFor(String os, String arch) {
			if(natives == null) throw new UnsupportedOperationException("no 'natives' table in dep " + name);
			if(downloads == null) throw new UnsupportedOperationException("no downloads for dep " + name);
			if(downloads.classifiedArtifacts == null) throw new UnsupportedOperationException("no classified downloads for dep " + name);
			
			String classifier = natives.get(os).getAsString();
			if(classifier == null) throw new UnsupportedOperationException("no native classifier for OS " + os + " in dep " + name);
			
			//e.g., 1.7.10's tv.twitch:twitch-platform dep
			classifier = classifier.replace("${arch}", arch);
			
			LibraryArtifact classifiedArtifact = downloads.classifiedArtifacts.get(classifier);
			if(classifiedArtifact == null) throw new UnsupportedOperationException("no artifact for classifier " + classifier + " in dep " + name);
			
			return classifiedArtifact;
		}
	}

	//TODO: doesn't check OS version ranges at all
	private static class Rule {
		public String action;
		public OS os;

		private static class OS {
			String name;
		}
	}
}
