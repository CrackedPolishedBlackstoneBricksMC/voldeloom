package net.fabricmc.loom.mcp;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.fabricmc.loom.util.Gdiff;
import org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream;
import org.tukaani.xz.LZMAInputStream;

public class Binpatch {
	public String originalFilename; //my own debug logging only

	public String sourceClassName;
	public boolean existsAtTarget; //<- if `false`, the patch expects to be applied to a zero-byte input and creates a brand new file at sourceClassName
	public byte[] patchBytes;
	
	//see ClassPatchManager#readPatch. It's the same among 1.6.4 and 1.7.10.
	public Binpatch read(String originalFilename, InputStream in) throws IOException {
		this.originalFilename = originalFilename;
		
		DataInputStream dataIn = new DataInputStream(in); //Not using try-with-resources. I do not want to close the provided stream.
		
		dataIn.readUTF(); //internal 'name' field, unused by both me and forge
		sourceClassName = dataIn.readUTF();
		dataIn.readUTF(); //targetClassName, we don't use it
		existsAtTarget = dataIn.readBoolean();
		if(existsAtTarget) dataIn.readInt(); //adler32 source class checksum, we don't use it

		int patchLength = dataIn.readInt();
		patchBytes = new byte[patchLength];
		dataIn.readFully(patchBytes);
		
		return this;
	}

	public byte[] apply(byte[] originalBytes) {
		return Gdiff.apply(originalBytes, patchBytes);
	}

	public static class Patchset {
		private final Map<String, List<Binpatch>> modifications = new HashMap<>();
		private final List<Binpatch> additions = new ArrayList<>();
		public int modificationCount; //for logging

		private void add(Binpatch patch) {
			if(patch.existsAtTarget) {
				//Forge technically supports multiple binpatches applied to the same class. Patch order is based off zip encounter order.
				//Thing is, I don't think Forge actually uses the feature.
				modifications.computeIfAbsent(patch.sourceClassName.replace('.', '/'), __ -> new ArrayList<>(1)).add(patch);
				modificationCount++;
			} else {
				//This patch invents a class out of thin air.
				//Forge saves these into the same collection as the modification-style binpatches, since it does binpatching on-the-fly.
				//We're doing patching ahead-of-time though, so it's alright to just dump these all into a list.
				additions.add(patch);
			}
		}

		//All binpatches that existAtTarget for the given target
		public List<Binpatch> getPatchesFor(String sourceClassInternalName) {
			List<Binpatch> patches = modifications.get(sourceClassInternalName);
			return patches == null ? Collections.emptyList() : patches;
		}

		//All binpatches that don't existAtTarget
		public List<Binpatch> getAdditions() {
			return additions;
		}
	}

	public static class Pack {
		public final Patchset client = new Patchset();
		public final Patchset server = new Patchset();

		/**
		 * Reads a Forge binpatches file, "binpatches.pack.lzma". The patches are wrapped in a jar, which is wrapped in pack200,
		 * which is wrapped further in a layer of LZMA compression. I guess they were really worried about filesize?
		 *
		 * Previous versions of voldeloom worked around a bug in commons-compress's Pack200CompressorInputStream.
		 * https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom/blob/10cb0c2f1d51c0570902e16d410868114baaba03/src/main/java/net/fabricmc/loom/mcp/BinpatchesPack.java#L63-L92
		 * This bug appears to be fixed.
		 */
		public Pack read(Path binpatchesPackLzma) {
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
					Patchset target;
					if(entry.getName().startsWith("binpatch/client/")) target = client;
					else if(entry.getName().startsWith("binpatch/server/")) target = server;
					else continue;

					target.add(new Binpatch().read(entry.getName(), binpatchesJar));
				}
			} catch (Exception e) {
				throw new RuntimeException("Failed to read binpatches.pack.lzma: " + e.getMessage(), e);
			}

			return this;
		}
	}
}
