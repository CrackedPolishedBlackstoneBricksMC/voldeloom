package net.fabricmc.loom.util.mcp;

import com.google.common.io.ByteStreams;
import net.fabricmc.loom.WellKnownLocations;
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream;
import org.gradle.api.Project;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class BinpatchesPack {
	public void read(Project p, Path binpatchesPackLzma) throws IOException {
		try(InputStream binpatchesPackLzmaIn = Files.newInputStream(binpatchesPackLzma);
		    InputStream binpatchesPackIn = new LZMACompressorInputStream(binpatchesPackLzmaIn);
		    InputStream binpatchesIn = new Pack200CompressorInputStream(binpatchesPackIn)) {
			
			p.getLogger().lifecycle("--> Decompressing {}", binpatchesPackLzma);
			
			byte[] extract = ByteStreams.toByteArray(binpatchesIn);
			Files.write(WellKnownLocations.getUserCache(p).resolve("binpatches.jar"), extract);

			throw new RuntimeException("TODO: Implement the rest of it lol");
		}
	}
}
