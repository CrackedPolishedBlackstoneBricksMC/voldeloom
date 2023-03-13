Notes and documents authored throughout the project. 🌟 denotes Good files that will probably have the most value to other people.

# documentation of Forge/MCP

### `binpatches.pack.lzma.md` 🌟

Couple notes about the file format used for vanilla-patching in forge 1.6 and 1.7

### `cleanargo.md`

A bit about the history of the `cleanargo` task in Forge Ant and which libraries in specific were cleaned

### `coremod discovery.md`

How Forge 1.4.7 loaded coremods

### `csvs and srgs.md` 🌟

History and file format of the MCP data files: `joined.srg`, `client.srg`/`server.srg`, `fields.csv`, `methods.csv`, and so on

### `getappdir.md`

Of course 1 of the 4 1.4.7 methods that fails to decompile with Fernflower is a critically important one to figuring out how the game starts up

### `how does the mcp work.md` 🌟

Documentation of the MCP project itself, what the binaries and scripts all do, exactly how it performs remapping, etc

### `release namespaces.md`

What Forge versions distributed mods with proguarded names, and when did it switch to intermediaries

### `working with mappings.md`

*Intended* to be a document about "what data structures make sense to efficiently write software operating on mappings" but i don't think this document is very good.

# documentation of this project

### `fernflower interface.md`

how Fernflower *used* to be invoked in loom 0.4 (outdated)

### `old notes.md`

Mixed documentation, mostly about how Loom 0.4 worked and trying to reverse-engineer for myself *why* it worked

### `stealing from loom.layered.md`

Reverse-engineering of `loom.layered` system so i can build something similar with `volde.layered`

### `what is using providers.md`

notes taken during a `NewProvider` system refactor, all outdated by now

# debugging

### `1.7.10 weird innerclasses.md`

resolving a bug in how inner classes got remapped

### `memory consumption.md`

Weighing the in-memory MCP format used in Voldeloom.

### `switchmap_through_javap.txt`

Disassembly of an "enum switchmap class" (there was a short time where I thought i'd have to synthesize my own)

### `weird enum switch methods.md`

Methods that fail to decompile (and previously, crashed the game before i fixed the remapper)

### `why are ou doing taht.md`

Downloading files with java and decompressing them into completely different files! What!