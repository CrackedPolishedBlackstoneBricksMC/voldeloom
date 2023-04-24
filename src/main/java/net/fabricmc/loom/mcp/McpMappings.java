package net.fabricmc.loom.mcp;

/**
 * "MCP mappings" is a loose name for a collection of these files:
 * <ul>
 *   <li>joined.srg, or both client.srg and server.srg</li>
 *   <li>fields.csv</li>
 *   <li>methods.csv</li>
 * </ul>
 * 
 * This class does not handle packages.csv; they are folded into joined.srg at build time.
 * 
 * @see McpMappingsBuilder
 */
public class McpMappings {
	public McpMappings(Srg joined, Srg client, Srg server, Members fields, Members methods) {
		this.joined = joined;
		this.client = client;
		this.server = server;
		this.fields = fields;
		this.methods = methods;
	}
	
	public final Srg joined;
	public final Srg client;
	public final Srg server;
	public final Members fields;
	public final Members methods;
	
	//in 1.2 there's four files to keep track of: client, server, fields, methods.
	//The fields and method files are shared across both sides, but the srgs are not.
	//in 1.3+ there's only one srg, which is not the same as either the client or server srgs.
	//and yeah, this is some "stringly typed coding"
	public Srg chooseSrg(String srgName) {
		switch(srgName) {
			case "client": return client;
			case "server": return server;
			case "joined": return joined;
			default: throw new IllegalArgumentException("pick 'client', 'server', or 'joined', not '" + srgName + "'");
		}
	}
}
