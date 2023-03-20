package net.fabricmc.loom.util;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

//TODO: a bit less crummy (maybe manual gson-wrangling, maybe don't allocate crap for *every* minecraft version *ever* because we're only interested in one)
public class ManifestIndex {
	public static ManifestIndex read(Path path) throws IOException {
		try(BufferedReader reader = Files.newBufferedReader(path)) {
			ManifestIndex manifestIndex = new Gson().fromJson(reader, ManifestIndex.class);
			
			manifestIndex.versionList.forEach(v -> manifestIndex.versions.put(v.id.toLowerCase(Locale.ROOT), v));
			
			return manifestIndex;
		}
	}
	
	@SerializedName("versions") public List<VersionData> versionList = new ArrayList<>();
	public transient Map<String, VersionData> versions = new HashMap<>();
	
	public static class VersionData {
		public String id, url;
	}
}
