package net.fabricmc.loom.util.mcp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class Binpatch {
	public String originalEntryName;
	public String name;
	public String sourceClassName;
	public String targetClassName;
	public boolean existsAtTarget;
	public int checksum;
	public int patchLength;
	public byte[] patchBytes;
	
	//see ClassPatchManager#readPatch. It's the same among 1.6.4 and 1.7.10.
	public Binpatch read(String originalEntryName, InputStream in) throws IOException {
		this.originalEntryName = originalEntryName;
		
		DataInputStream dataIn = new DataInputStream(in); //Not using try-with-resources. I do not want to close the provided stream.
		
		name = dataIn.readUTF();
		sourceClassName = dataIn.readUTF();
		targetClassName = dataIn.readUTF();
		existsAtTarget = dataIn.readBoolean();
		if(existsAtTarget) checksum = dataIn.readInt();
		patchLength = dataIn.readInt();
		
		patchBytes = new byte[patchLength];
		dataIn.readFully(patchBytes);
		
		return this;
	}
	
	//The gdiff algorithm is described at https://www.w3.org/TR/NOTE-gdiff-19970825.html .
	//It's merely a note, not a published standard.
	public byte[] apply(byte[] originalBytes) {
		ByteArrayInputStream patch = new ByteArrayInputStream(patchBytes);
		//empirically: this estimate for the output size fits most 1.6.4 binpatches, and the ones that don't fit only grow the array once
		//here's a plot of "(size after patch/size before patch)" on forge 1.6: https://i.imgur.com/f41kemy.png
		//the smallest is client GuiControls (0.7x) and the largest is server WorldProvider (4.1x)
		ByteArrayOutputStream out = new ByteArrayOutputStream((int) (originalBytes.length * 2.2));
		
		int magic = readMagic(patch);
		if(magic != 0xD1FFD1FF) throw new RuntimeException("Invalid magic: " + Integer.toHexString(magic) + ", expected 0xD1FFD1FF");
		
		int version = readUbyte(patch);
		if(version != 4) throw new RuntimeException("Invalid version: " + version + ", expected version 4");
		
		done: while(true) {
			int instruction = readUbyte(patch);
			if(instruction == -1) throw new RuntimeException("Unexpected end-of-patch");
			if(instruction > 255) throw new RuntimeException("Not a byte: " + instruction);
			
			switch(instruction) {
				//Instruction 0: end.
				case 0: break done;
				
				//Instructions 1..=246: copy [instruction] many bytes from patch to output.
				//Instructions 247/248: read (ushort/uint), copy that many bytes from patch to output.
				default:  copyFromPatch(patch, instruction,       out); break; //<- forge patches use this
				case 247: copyFromPatch(patch, readUshort(patch), out); break; //<- forge patches use this
				case 248: copyFromPatch(patch, readInt(patch),    out); break;
				
				//Instructions 249..=255: copy a segment of the original file into the output.
				//first read "absolute byte offset in original file", then read "length to copy".
				//Data types vary per-instruction to accomodate different sizes of number.
				case 249: out.write(originalBytes, readUshort(patch),    readUbyte(patch));  break; //<- forge patches use this
				case 250: out.write(originalBytes, readUshort(patch),    readUshort(patch)); break;
				case 251: out.write(originalBytes, readUshort(patch),    readInt(patch));    break;
				case 252: out.write(originalBytes, readInt(patch),       readUbyte(patch));  break;
				case 253: out.write(originalBytes, readInt(patch),       readUshort(patch)); break;
				case 254: out.write(originalBytes, readInt(patch),       readInt(patch));    break;
				case 255: out.write(originalBytes, readTruncLong(patch), readInt(patch));    break;
			}
		}
		
		return out.toByteArray();
	}
	
	private void copyFromPatch(ByteArrayInputStream patch, int howMany, ByteArrayOutputStream out) {
		byte[] buffer = new byte[howMany];
		int actuallyRead = patch.read(buffer, 0, buffer.length);
		if(actuallyRead != buffer.length) throw new RuntimeException("I thought ByteArrayInputStream.read() always filled the buffer");
		out.write(buffer, 0, buffer.length);
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
	
	//We assume the input file fits in a Java array (<2gb), so if we get a `long` we can't use it to index anyway.
	private int readTruncLong(ByteArrayInputStream in) {
		long result = readBigEndian(in, 8, "long");
		if(result < 0 || result > Integer.MAX_VALUE) throw new RuntimeException("long that can't be truncated to an int");
		return (int) result;
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
}
