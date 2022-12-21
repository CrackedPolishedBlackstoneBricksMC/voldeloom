# Voldeloom-disastertime

Gradle plugin for ~~Fabric~~ ancient versions of Forge. Based on an ancient version of Fabric Loom.

This is the `disaster-time` branch, aka "quat's playground". Here be dragons. It's me. I'm the dragon.

## Sample projects

There doesn't seem to be a nice way to develop a Gradle plugin and actually *use* the plugin to see if it works (no, not "write automated tests for the plugin", *actually* use it) at the same time. This is because Gradle is a terrible piece of shit.

Apparently the actual recommended way to do this is a) shove everything in `buildSrc` because imagine actually wanting to publish your plugin lol, or b) literally just publishing it to `mavenLocal` any time you do anything, and i guess ??hope the change gets picked up?? when you import the plugin by-name from an unrelated test project? Both of these are fucking ridiculous, imo.

So: 

* Sample projects contain a line in `settings.gradle` that includes the main voldeloom project as an "included build". Note that this feels a bit backwards because the subfolder is "including" the parent folder. It is what it is.
* In IDEA, you can right-click on each sample project's `build.gradle` and press "Link Gradle Project" towards the bottom of the dropdown. It's like how IntelliJ is able to discover subprojects and put them in the gradle tool window, but it needs a bit of manual assistance cause this isn't a subproject. Then you get gradle IDE integration. Works better than I expect it to, in this obvious nightmare scenario.
* Note that the plugin will be *compiled against* the version of Gradle used in the sample project. I had to blindly rewrite some legacy-handling code to use reflection because the method was removed. Will see what I can do.

## Debugging the plugin

idk lol. Println.

I think gradle internally supports remote debugging through a system property

## Common problems (for consumers)

*Weird NPEs when only sketching in the build.gradle:* Currently it crashes if a set of mappings is not defined. Ideally it should skip mappings-related tasks instead, or use some kind of passthrough mappings instead of `null`. Hm.

*`Failed to provide null:unspecified:null : conf/packages.csv`:* Mapping parsing, jar remapping, or something else in that area blew up. If this failed due to missing `packages.csv`, use a mcp zip merged with a forge zip; this is janky as hell I know you shouldn't have to do this. want to fix that.

# Just taking notes

How far is it safe to diverge from Loom's practices? I mean obviously there's no point in trying to keep up with upstream changes in Loom patch-for-patch, we're way too far gone by this point. Some stuff is the way it is due to this project's history as a cursed frankenstein plugin that was taped together, other stuff is the way it is for a good reason.

Same for "removing remnants of fabric", it's very possible that someone could make some "fabric loader on top of forge" bullshit and i don't want to close the door to that right away lolll

## differences between disaster-time and other voldelooms

* i auto configure the forge maven repository
  * maybe this isn't such a good idea to do automatically? break it out into a helper function
  * basically it needs a little `metadataSources` workaround on gradle 5+ and it's annoying
* cryptic corrupt-zip errors when your mcp zip is fucked: i at least throw an exception when i see a 0 byte file

### and things i want to add

Figure out why sources don't attach themself correctly in intellij, "find usages" is broken, etc

try and clean things up, remove remnants of fabric where appropriate

Run configs would be an obvious huge bonus... lol

## gradle support woes

Gradle 7 has `toolchains`, a very appealing feature that allows the build environment to use an arbitrary version of java. in Hoppers I used a Java 11 build environment because it was the newest one that can target Java 6 bytecode, and it worked WAY better than i thought it would. gradle 7 also has other breaking changes that make it kinda a pain to deal with sometimes. It does support java 8 execution environments.

Gradle 4 doesn't have support for `includeGroup`, which blocks my plan to provide MCP zips from the Internet Archive as an Ivy repository, because IA will spam your gradle cache with zero byte files on 404. Old versions of Gradle also don't work with modern versions of java due to jigsaw bullshit. Did gradle 4 run on java 6? I don't think so? If it did, I could make some point about "there's something to be said for developing against a Java 6 game using only Java 6 technologies" which would be a point against moving primary support to gradle 7, but i don't think it did

Loom used Gradle 4 up until april 2021 https://github.com/FabricMC/fabric-loom/pull/380 but mainly for legacy reasons i think

The primary benefit to maintaining support for old Gradles is for retrofitting existing ancient projects onto this build system, but most of those projects predate the invention of Gradle itself

## tools voldeloom uses

Taking note of this because like, if i choose to use `tiny-remapper` to perform jar remapping i must output mappings in a format `tiny-remapper` can understand, but using a different tool would imply a different format, etc

* `LineNumberRemapper` is an objectweb asm class parser with a note saying that it should actually be in FabricMC Stitch (which reminds me, i should check for updates on the tools)
* `MapJarsTiny` and `ModProcessor` use tiny-remapper
* `SourceRemapper` uses of course cadixdev Mercury and Lorenz to perform source remapping
* `TinyRemapperMappingsHelper` is a, thing
* Decompilation is done with their fork of Fernflower, includes api for getting javadoc and the like into the jar. I don't know if mcp mappings provide comments data
* `MigrateMappingsTask` can honestly probably be deleted we are stuck with mcp, but it uses the sourceremapper class and some weird deps like `net.fabricmc.mapping` (tiny-mappings-parser) and `net.fabricmc.lorenztiny` (lorenz-tiny)
* (important) `RemapJarTask` uses tiny-remapper
* `RemapLineNumbersTask` uses stitch and the linenumberremapper util class

and the current set of forge extensions

* all things in `asm` are unsurprisingly objectweb asm class parsers, as well as `ASMFixesProcessor` and `CursedLibDeleterProcessor`
* `AcceptorProvider`, `CsvApplierAcceptor`, `SrcMappingProvider` are tiny-remapper glue code?
  * `SrgMappingProvider` also includes an asm parser to read off the list of inner classes and fields from the minecraft jar ("calcInfo") which appears to be important to successfully parse the srg
* `TinyWriter3Column` is used to write an `official intermediary named` file, which miiiiight be retrieved later for doing remapping somewhere idk (it's "mappings_final")
* `ForgeATProvider` is a tiny remapper imappingprovider acceptor thingie

## ?

what is a fabric installer json? (LoomDependencyManager) probably something to do with run configs

`ModCompileRemapper` specifically looks for fabric mods, i think this has to do with mod dependencies

It's probably safe to delete instances of jij stuff because Forge does not natively support nested jars

abstractdecompiletask uses a "line map file"