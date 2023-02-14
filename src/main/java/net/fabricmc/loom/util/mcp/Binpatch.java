package net.fabricmc.loom.util.mcp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class Binpatch {
	public String name;
	public String sourceClassName;
	public String targetClassName;
	public boolean exists;
	public int checksum;
	public int patchLength;
	public byte[] patchBytes;
	
	//see ClassPatchManager#readPatch. It's the same among 1.6.4 and 1.7.10.
	public Binpatch read(InputStream in) throws IOException {
		DataInputStream dataIn = new DataInputStream(in); //Not using try-with-resources. I do not want to close the provided stream.
		
		name = dataIn.readUTF();
		sourceClassName = dataIn.readUTF();
		targetClassName = dataIn.readUTF();
		exists = dataIn.readBoolean();
		if(exists) checksum = dataIn.readInt();
		patchLength = dataIn.readInt();
		
		patchBytes = new byte[patchLength];
		dataIn.readFully(patchBytes);
		
		return this;
	}
	
	// https://www.w3.org/TR/NOTE-gdiff-19970825.html
	public byte[] apply(byte[] originalBytes) {
		ByteArrayInputStream patch = new ByteArrayInputStream(patchBytes);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		
		int magic = readMagic(patch);
		if(magic != 0xD1FFD1FF) throw new RuntimeException("Invalid magic: " + Integer.toHexString(magic) + ", expected 0xD1FFD1FF");
		
		int version = readUbyte(patch);
		if(version != 4) throw new RuntimeException("Invalid version: " + version + ", expected version 4");
		
		int instruction;
		while(true) {
			instruction = patch.read();
			if(instruction == -1) throw new RuntimeException("Unexpected end-of-patch");
			if(instruction > 255) throw new RuntimeException("Not a byte: " + instruction);
			
			//instruction 0: end-of-file
			if(instruction == 0) break;
			
			//instructions 1 through 246: copy that many bytes from the patch to the output
			if(instruction <= 246) {
				byte[] buffer = exactlyNBytes(patch, instruction);
				out.write(buffer, 0, buffer.length);
				continue;
			}
			
			//instruction 247 and 248: read a big-endian number, then copy that many bytes from the patch to the output
			//247 reads a ushort and 248 reads an int
			if(instruction == 247 || instruction == 248) {
				int length = instruction == 247 ? readUshort(patch) : readInt(patch);
				byte[] buffer = exactlyNBytes(patch, length);
				out.write(buffer, 0, length);
				continue;
			}
			
			//instructions 249 through 255: copy some bytes from the original file to the output
			//first parameter: absolute position in the source to copy from, second parameter: source length
			//the data types vary per instruction
			int copySource, copyLength;
			switch(instruction) {
				case 249: copySource = readUshort(patch); copyLength = readUbyte(patch);  break;
				case 250: copySource = readUshort(patch); copyLength = readUshort(patch); break;
				case 251: copySource = readUshort(patch); copyLength = readInt(patch);    break;
				case 252: copySource = readInt(patch);    copyLength = readUbyte(patch);  break;
				case 253: copySource = readInt(patch);    copyLength = readUshort(patch); break;
				case 254: copySource = readInt(patch);    copyLength = readInt(patch);    break;
				case 255:
					//java arrays are not indexable by longs anyway
					long copySourceLong = readLong(patch);
					if(copySourceLong < 0 || copySourceLong > Integer.MAX_VALUE) throw new RuntimeException("Too big!");
					copySource = (int) copySourceLong;      copyLength = readInt(patch);    break;
				default:
					//intellij knows that this table handles all cases, but javac doesn't!
					throw new RuntimeException("Unreachable");
			}
			
			out.write(originalBytes, copySource, copyLength);
		}
		
		return out.toByteArray();
	}
	
	private int readUbyte(ByteArrayInputStream in) {
		return in.read();
	}
	
	private int readUshort(ByteArrayInputStream in) {
		return (int) readBigEndian(in, 2, "ushort");
	}
	
	private int readMagic(ByteArrayInputStream in) {
		return (int) readBigEndian(in, 4, "magic number");
	}
	
	//"If a number larger than 1^31-1 bytes is needed for a command command that takes only int arguments,
	//the command must be split into multiple commands.". hehe. png has something similar.
	//this clause, in standards, is known as the "i bet you're using some shitty language without unsigned types" clause
	private int readInt(ByteArrayInputStream in) {
		int result = (int) readBigEndian(in, 4, "int");
		if(result < 0) throw new RuntimeException("int with the high bit set");
		return result;
	}
	
	private long readLong(ByteArrayInputStream in) {
		return readBigEndian(in, 8, "long");
	}
	
	private long readBigEndian(ByteArrayInputStream in, int bytes, String type) {
		long result = 0;
		for(int i = 0; i < bytes; i++) {
			result <<= 8;
			
			int read = in.read();
			if(read == -1) throw new RuntimeException("Unexpected end of file (byte " + i + " of " + type + ")");
			
			result |= read;
		}
		return result;
	}
	
	private byte[] exactlyNBytes(ByteArrayInputStream in, int bytes) {
		byte[] buffer = new byte[bytes];
		int actuallyRead = in.read(buffer, 0, buffer.length);
		if(actuallyRead != buffer.length) throw new RuntimeException("I thought ByteArrayInputStream always read exactly the right number of bytes");
		return buffer;
	}
}
