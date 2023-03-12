package net.fabricmc.loom.mcp;

import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream;
import org.gradle.api.logging.Logger;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class BinpatchesPack {
	public final Map<String, Binpatch> clientBinpatches = new LinkedHashMap<>();
	public final Map<String, Binpatch> serverBinpatches = new LinkedHashMap<>();
	
	public BinpatchesPack read(Logger log, Path binpatchesPackLzma) {
		try(InputStream binpatchesPackLzmaIn = new BufferedInputStream(Files.newInputStream(binpatchesPackLzma));
		    InputStream lzmaDecompressor = new LZMACompressorInputStream(binpatchesPackLzmaIn);
		    InputStream pack200Decompressor = new Pack200CompressorInputStream(new OpenSesameInputStream(lzmaDecompressor));
		    ByteArrayOutputStream binpatchesJarBytes = new ByteArrayOutputStream()) {
			
			log.info("--> Decompressing {}...", binpatchesPackLzma);
			
			//standard java boilerplate to pour one stream into another
			byte[] shuttle = new byte[4096];
			int readBytes;
			while((readBytes = pack200Decompressor.read(shuttle)) != -1) binpatchesJarBytes.write(shuttle, 0, readBytes);
			
			log.info("--> Parsing decompressed jar...");
			byte[] binpatchesJarByteArray = binpatchesJarBytes.toByteArray();
			
			//Here, using oldschool JarInputStream instead of zip filesystem, because the jar doesn't exist on-disk.
			try(ZipInputStream binpatchesJar = new ZipInputStream(new ByteArrayInputStream(binpatchesJarByteArray))) {
				ZipEntry entry;
				while((entry = binpatchesJar.getNextEntry()) != null) {
					if(entry.isDirectory() || !entry.getName().endsWith(".binpatch")) continue;
					
					//what's the worst that could happen
					Map<String, Binpatch> target;
					if(entry.getName().startsWith("binpatch/client/")) target = clientBinpatches;
					else if(entry.getName().startsWith("binpatch/server/")) target = serverBinpatches;
					else continue;
					
					Binpatch binpatch = new Binpatch().read(entry.getName(), binpatchesJar);
					target.put(binpatch.sourceClassName.replace('.', '/'), binpatch);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to read binpatches.pack.lzma: " + e.getMessage(), e);
		}
		
		return this;
	}
	
	/*
	 * The commons-compress pack200 implementation has a correctness bug where it blindly assumes read(byte[], int, int)
	 * will *always* return *exactly* the requested number of bytes. It is legal for this method to return
	 * fewer bytes, and in fact when passed basically any stream that doesn't implement the `available` hint,
	 * it will wrap it in such a way where it's almost guaranteed to get an InputStream that doesn't adhere
	 * to this assumption. We can work around this by looking carefully at how that wrapping code works,
	 * and crafting an inputstream that causes commons-compress to not shoot itself in the foot.
	 * See: https://github.com/apache/commons-compress/pull/360
	 */
	private static class OpenSesameInputStream extends FilterInputStream {
		public OpenSesameInputStream(InputStream wrapped) {
			super(wrapped);
		}
		
		@Override
		public boolean markSupported() {
			//Causes Segment#unpackRead to wrap me in a BufferedInputStream, because those do support marking
			return false;
		}
		
		@Override
		public int available() {
			//This method is rather obscure - You're supposed to estimate how many bytes are available to read immediately.
			//A return value of 0 doesn't mean the stream is depleted, it is just a hint that further calls to read() will block.
			//BufferedInputStream#read(byte[]) usually tries to fill the entire user-provided buffer "as a convenience", calling
			//the wrapped stream's read() until the whole buffer is full, but it doesn't want to block, so it will stop when the
			//wrapped stream's `available()` returns 0. Let's just... not make it return 0, so it always fills the entire user-provided buffer.
			return Integer.MAX_VALUE;
		}
	}
}
