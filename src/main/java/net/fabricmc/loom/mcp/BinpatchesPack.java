package net.fabricmc.loom.mcp;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream;
import org.gradle.api.logging.Logger;
import org.tukaani.xz.LZMAInputStream;

public class BinpatchesPack {
	//Keys are classes in "internal name" format.
	public final Map<String, Binpatch> clientBinpatches = new LinkedHashMap<>();
	public final Map<String, Binpatch> serverBinpatches = new LinkedHashMap<>();

	/**
	 * This method reads a Forge binpatches file, "binpatches.pack.lzma".
	 * The patches are wrapped in a jar, which is wrapped in pack200, which is wrapped further in a layer of LZMA compression.
	 * I guess they were really worried about filesize.
	 *
	 * Previous versions of voldeloom worked around a bug in commons-compress's Pack200CompressorInputStream.
	 * https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom/blob/10cb0c2f1d51c0570902e16d410868114baaba03/src/main/java/net/fabricmc/loom/mcp/BinpatchesPack.java#L63-L92
	 * This bug appears to be fixed.
	 */
	public BinpatchesPack read(Logger log, Path binpatchesPackLzma) {
		try(InputStream binpatchesPackLzmaIn = new BufferedInputStream(Files.newInputStream(binpatchesPackLzma));
		    InputStream lzmaDecompressor = new LZMAInputStream(binpatchesPackLzmaIn);
		    InputStream pack200Decompressor = new Pack200CompressorInputStream(lzmaDecompressor);
		    ZipInputStream binpatchesJar = new ZipInputStream(pack200Decompressor)
		) {
			ZipEntry entry;
			while((entry = binpatchesJar.getNextEntry()) != null) {
				if(entry.isDirectory() || !entry.getName().endsWith(".binpatch")) continue;

				//Forge, at startup, reads either from binpatch/client/ or binpatch/server/ based on physical side.
				//Let's read both patchsets at the same time.
				Map<String, Binpatch> target;
				if(entry.getName().startsWith("binpatch/client/")) target = clientBinpatches;
				else if(entry.getName().startsWith("binpatch/server/")) target = serverBinpatches;
				else continue;

				Binpatch binpatch = new Binpatch().read(entry.getName(), binpatchesJar);
				target.put(binpatch.sourceClassName.replace('.', '/'), binpatch);
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to read binpatches.pack.lzma: " + e.getMessage(), e);
		}

		return this;
	}
}
