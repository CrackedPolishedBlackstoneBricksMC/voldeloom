package net.fabricmc.loom.mcp;

import net.fabricmc.loom.util.StringInterner;

import javax.annotation.Nullable;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parser for fields.csv and methods.csv (they have the same format).
 */
public class Members {
	private final Map<String, Entry> members = new HashMap<>();
	
	public Members read(Path path, StringInterner mem) throws IOException {
		List<String> lines = Files.readAllLines(path);
		for(int i = 0; i < lines.size(); i++) {
			String line = lines.get(i).trim();
			if(line.isEmpty()) continue;
			if((i == 0 && "searge,name,side,desc".equals(line))) continue; //the csv header
			int lineNo = i + 1;
			
			//Example fields.csv lines: (methods.csv has the same format)
			// field_70136_U,lastTickPosZ,2,"The entity's Z coordinate at the previous tick, used to calculate position during rendering routines"
			// field_70129_M,yOffset,2,
			
			String[] split = line.split(",", 4); //limit to 4 splits to try and hold the comment field together
			
			if(split.length < 3) {
				System.err.println("line " + lineNo + " is too short: " + line);
				continue;
			}
			
			//Parse this field as an int
			int parsedSide;
			try {
				parsedSide = Integer.parseInt(split[2]);
			} catch (NumberFormatException e) {
				System.err.println("line " + lineNo + " has unparseable side: " + line);
				continue;
			}
			
			//and record the entry
			members.put(mem.intern(split[0]), new Entry(
				mem.intern(split[1]),
				parsedSide,
				//Even though the csv has a trailing comma on fields without a comment,
				//java's split() method will not include an empty string at the end of the array, it will just return a shorter array.
				//Also, lets not waste time trying to intern the comment, they're likely to be unique.
				split.length == 3 ? null : split[3])
			);
		}
		
		return this;
	}
	
	public void writeTo(Path path) throws IOException {
		try(
			OutputStream o = new BufferedOutputStream(Files.newOutputStream(path));
			OutputStreamWriter w = new OutputStreamWriter(o)
		) {
			write(w);
			o.flush();
		}
	}
	
	public void write(OutputStreamWriter o) throws IOException {
		o.write("searge,name,side,desc\n");
		for(Map.Entry<String, Entry> e : members.entrySet()) {
			o.write(e.getKey());
			o.write(',');
			o.write(e.getValue().remappedName);
			o.write(',');
			o.write(Integer.toString(e.getValue().side));
			o.write(',');
			String comment = e.getValue().comment; if(comment != null) o.write(comment);
			o.write('\n');
		}
	}
	
	public boolean isEmpty() {
		return members.isEmpty();
	}
	
	public @Nullable Entry remapSrg(String srg) {
		return members.get(srg);
	}
	
	public static class Entry {
		public final String remappedName;
		public final int side;
		public final @Nullable String comment;
		
		public Entry(String remappedName, int side, String comment) {
			this.remappedName = remappedName;
			this.side = side;
			
			if(comment == null || comment.trim().isEmpty()) {
				this.comment = null;
			} else {
				this.comment = comment;
			}
		}
		
		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;
			Entry entry = (Entry) o;
			return side == entry.side && remappedName.equals(entry.remappedName) && Objects.equals(comment, entry.comment);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(remappedName, side, comment);
		}
	}
}
