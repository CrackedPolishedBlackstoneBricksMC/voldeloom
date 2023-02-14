package net.fabricmc.loom.util.mcp;

import net.fabricmc.loom.WellKnownLocations;
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream;
import org.gradle.api.Project;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class BinpatchesPack {
	public BinpatchesPack read(Project p, Path binpatchesPackLzma) {
		try(InputStream binpatchesPackLzmaIn = new BufferedInputStream(Files.newInputStream(binpatchesPackLzma));
		    InputStream lzmaDecompressor = new LZMACompressorInputStream(binpatchesPackLzmaIn);
		    //The commons-compress pack200 implementation has a bug where it blindly assumes read(byte[], int, int)
		    //will *always* return *exactly* the requested number of bytes. It is legal for this method to return
		    //fewer bytes, and in fact LZMACompressorInputStream sometimes does. To work around this, let's pass
		    //everything through an InputStream that adheres to this assumption.
		    //See: https://github.com/apache/commons-compress/pull/360
		    InputStream pack200Decompressor = new Pack200CompressorInputStream(new OpenSesameInputStream(lzmaDecompressor));
		    ByteArrayOutputStream binpatchesJar = new ByteArrayOutputStream()) {
			
			p.getLogger().lifecycle("--> Decompressing {}...", binpatchesPackLzma);
			
			//standard java boilerplate to pour one stream into another
			byte[] shuttle = new byte[4096];
			int readBytes;
			while((readBytes = pack200Decompressor.read(shuttle)) != -1) binpatchesJar.write(shuttle, 0, readBytes);
			
			Files.write(WellKnownLocations.getUserCache(p).resolve("binpatches.jar"), binpatchesJar.toByteArray());
				
			if(true) throw new RuntimeException("TODO: Implement the rest of it lol");
		} catch (Exception e) {
			throw new RuntimeException("Failed to read binpatches.pack.lzma: " + e.getMessage(), e);
		}
		
		return this;
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
