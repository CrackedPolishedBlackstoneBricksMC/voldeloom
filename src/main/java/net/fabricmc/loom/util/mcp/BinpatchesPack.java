package net.fabricmc.loom.util.mcp;

import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream;
import org.gradle.api.Project;

import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
	
	public BinpatchesPack read(Project p, Path binpatchesPackLzma, Srg srg, @Nullable Packages packages) {
		try(InputStream binpatchesPackLzmaIn = new BufferedInputStream(Files.newInputStream(binpatchesPackLzma));
		    InputStream lzmaDecompressor = new LZMACompressorInputStream(binpatchesPackLzmaIn);
		    //The commons-compress pack200 implementation has a correctness bug where it blindly assumes read(byte[], int, int)
		    //will *always* return *exactly* the requested number of bytes. It is legal for this method to return
		    //fewer bytes, and in fact when passed basically any stream that doesn't implement the `available` hint,
		    //it will wrap it in such a way where it's almost guaranteed to get an InputStream that doesn't adhere
		    //to this assumption. We can work around this by looking carefully at how that wrapping code works,
		    //and crafting an inputstream that causes commons-compress to not shoot itself in the foot.
		    //See: https://github.com/apache/commons-compress/pull/360
		    InputStream pack200Decompressor = new Pack200CompressorInputStream(new OpenSesameInputStream(lzmaDecompressor));
		    ByteArrayOutputStream binpatchesJarBytes = new ByteArrayOutputStream()) {
			
			p.getLogger().lifecycle("--> Decompressing {}...", binpatchesPackLzma);
			
			//standard java boilerplate to pour one stream into another
			byte[] shuttle = new byte[4096];
			int readBytes;
			while((readBytes = pack200Decompressor.read(shuttle)) != -1) binpatchesJarBytes.write(shuttle, 0, readBytes);
			
			p.getLogger().lifecycle("--> Parsing decompressed jar...");
			byte[] binpatchesJarByteArray = binpatchesJarBytes.toByteArray();
			
			//Here, using oldschool JarInputStream instead of zip filesystem, because the jar doesn't exist on-disk.
			try(ZipInputStream binpatchesJar = new ZipInputStream(new ByteArrayInputStream(binpatchesJarByteArray))) {
				ZipEntry entry;
				while((entry = binpatchesJar.getNextEntry()) != null) {
					if(entry.isDirectory() || !entry.getName().endsWith(".binpatch")) continue;
					
					//what's the worst that could happen
					String name = entry.getName();
					name = name.substring(0, name.length() - ".binpatch".length());
					
					Map<String, Binpatch> target;
					if(name.startsWith("binpatch/client/")) {
						target = clientBinpatches;
						name = name.substring("binpatch/client/".length());
					} else if(name.startsWith("binpatch/server/")) {
						target = serverBinpatches;
						name = name.substring("binpatch/server/".length()); //okay yes they're the same length
					} else continue;
					
					//This looks wrong to me, but ZipInputStream apparently does some magic where when you read it,
					//it magically ends at the end of the current entry until you call getNextEntry again to unblock it. Sure. Okay.
					//These cranky old Java apis are fun.
					//ByteArrayOutputStream binpatch = new ByteArrayOutputStream();
					//while((readBytes = binpatchesJar.read(shuttle, 0, shuttle.length)) != -1) binpatch.write(shuttle, 0, readBytes);
					Binpatch binpatch = new Binpatch().read(binpatchesJar);
					
					target.put(name, binpatch);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to read binpatches.pack.lzma: " + e.getMessage(), e);
		}
		
		return this;
	}
	
	public BinpatchesPack unmap(Project p, Srg srg, @Nullable Packages packages) {
		//TODO: this is a KLUDGE!!
		// Binpatches are stored in files like `net.minecraft.block.BlockComparator.binpatch` i.e. it has the repackaged + mapped name
		// We need to figure out which unmapped class this belongs to
		// I don't really have a good system in for bidirectional mappings just yet, so, do it ad-hoc right now
		
		p.getLogger().lifecycle("--> Computing reverse class mappings...");
		Map<String, String> reverseNetMinecraftSrcClassMappings = new LinkedHashMap<>();
		srg.classMappings.forEach((official, nmsName) -> reverseNetMinecraftSrcClassMappings.put(nmsName, official));
		
		Map<String, String> reverseClassMappings;
		if(packages == null) {
			reverseClassMappings = reverseNetMinecraftSrcClassMappings;
		} else {
			reverseClassMappings = new LinkedHashMap<>();
			reverseNetMinecraftSrcClassMappings.forEach((nmsName, official) -> reverseClassMappings.put(packages.repackage(nmsName), official));
		}
		
		p.getLogger().lifecycle("--> Unmapping binpatches...");
		Map<String, Binpatch> mappedClientBinpatches = new LinkedHashMap<>(clientBinpatches);
		Map<String, Binpatch> mappedServerBinpatches = new LinkedHashMap<>(serverBinpatches);
		clientBinpatches.clear();
		serverBinpatches.clear();
		
		mappedClientBinpatches.forEach((mappedName, binpatch) -> {
			String unmappedName = mappedName.replace('.', '/');
			unmappedName = reverseClassMappings.getOrDefault(unmappedName, unmappedName);
			clientBinpatches.put(unmappedName, binpatch);
		});
		
		mappedServerBinpatches.forEach((mappedName, binpatch) -> {
			String unmappedName = mappedName.replace('.', '/');
			unmappedName = reverseClassMappings.getOrDefault(unmappedName, unmappedName);
			serverBinpatches.put(unmappedName, binpatch);
		});
		
		return this;
	}
	
	public static byte[] gdiff(byte[] source, Binpatch patch) {
		//TODO
		return source;
	}
	
	private static class OpenSesameInputStream extends InputStream {
		public OpenSesameInputStream(InputStream wrapped) {
			this.wrapped = wrapped;
		}
		
		private final InputStream wrapped;
		
		@Override
		public boolean markSupported() {
			//Causes Segment#unpackRead to wrap me in a BufferedInputStream, because those do support marking
			return false;
		}
		
		@Override
		public int available() throws IOException {
			//This method is rather obscure - You're supposed to estimate how many bytes are available to read immediately.
			//A return value of 0 doesn't mean the stream is depleted, it is just a hint that further calls to read() will block.
			//BufferedInputStream#read(byte[]) usually tries to fill the entire user-provided buffer "as a convenience", calling
			//the wrapped stream's read() until the whole buffer is full, but it doesn't want to block, so it will stop when the
			//wrapped stream's `available()` returns 0. Let's just... not make it return 0, so it always fills the entire user-provided buffer.
			return Integer.MAX_VALUE;
		}
		
		//Boring delegation boilerplate:
		@Override public int read(byte[] b) throws IOException { return wrapped.read(b); }
		@Override public int read(byte[] b, int off, int len) throws IOException { return wrapped.read(b, off, len); }
		@Override public int read() throws IOException { return wrapped.read(); }
		@Override public long skip(long n) throws IOException { return wrapped.skip(n); }
		@Override public void close() throws IOException { wrapped.close(); }
	}
}
