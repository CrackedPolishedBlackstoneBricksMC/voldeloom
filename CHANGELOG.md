Running changelog document, will be added to as I commit things.

# Next version: 2.3 (`agency.highlysuspect:voldeloom:2.3-SNAPSHOT`)

## Changes

* Rewrote basically all of the mappings guts
  * I researched how MCP actually works, instead of simply throwing `tiny-remapper` at everything
  * the goal is to use `tiny-remapper` where MCP uses RetroGuard, and an in-house `NaiveRenamer` tool where MCP uses regular expressions over the source code
    * `tiny-remapper` and RetroGuard are similar, modulo the mappings input format.
    * The main difference is that `NaiveRenamer` also operates over Java bytecode as well as source code, but uses the same "just find-and-replace" algorithm that ignores things like method descriptors.
    * deobf: `tiny-remapper` is used for the initial deobfuscation to SRG names, `NaiveRenamer` takes it the rest of the way to named
    * reobf: the SRG is extended to cover MCP names instead of just SRGs (mirroring a find-replace step in MCP), inverted, and `tiny-remapper` is used
  * Side effect: maybe improves `--refresh-dependencies` performance a bit, `NaiveRenamer` is very fast (because it doesn't do much) 
  * Deleted lots of tiny-remapper stuff that fell unused due to this change
* Reobf-to-srg also works in the new mapping system
  * release 1.5/1.6/1.7 mods again! maybe! (test them!!!)
  * quick note: setting `targetCompatibility = "6"` is *not enough* to strip some Java 8 anachronisms from class files if you use a java 8 compiler i think??
* `.srg` parser can also handle the more compact MCPBot `.csrg` format too
* Fixed cache soundness issues
  * Most files in your Gradle cache will now end in an 8-character hash of some metadata about their *provenance*. For example, `version_manifest_{HASH}.json`'s filename now carries the URL that the manifest was downloaded from (if using the customManifestUrl feature).
  * The metadata trickles into files derived from it, so `minecraft-1.4.7-client-{HASH}.jar`'s filename includes the same information, and so does the binpatched client, and the merged jar...
  * This fixes longstanding cache-coherency bugs, where changing the configuration of a file at the top of the tree would leave stale cache entries downstream from it, requiring a `--refresh-dependencies` to fix.
  * This also removes the need to store files in the per-project Gradle cache. Even if you use customized mappings in your project, it will not clash with other files in the cache.
  * I should probably expose the actual, not-hashed metadata somewhere
* Linemapping now works, fixing stacktrace line numbers and debugger breakpoints
  * didn't understand what it was before :sweat_smile:
  * After running genSources, might need to refresh your gradle to get your IDE to use the linemapped jar.
* fixed 1.7.10 asset loading
* Small performance/memory improvements
  * Forge binpatches only parsed when it's necessary to binpatch a jar
  * Reduced the amount of `Files.readAllBytes` calls, instead reading the files in chunks
  * Data structure used for `packages.csv` parsing is more memory-efficient
  * Mapping-related files are extracted from the mappings archive in a single pass

## Roadmap

* Fix 1.2.5 and make it "nice" (split sourcesets etc)
* Add more to the `volde.layered` system
* Figure out what's up with parameter name tables/asm4/targetCompatibility?
  * The current Auto Third Person buid uses gtnh forgegradle, i dropped it in my Blightfall (pre-CE) instance (Forge 10.13.2.1291, last version before the asm5 update) and it worked fine
  * The Voldeloom sample mod gets skipped though, because javac included a parameter name table that Forge's `ModClassVisitor` choked on. Why does gtnh fg work but mine doesnt
  * looks like the ATP jar uses local variable slots (according to intellij bytecode viewer) but samplemod is using the new parameter name system
  * why ?

# 2.2 (`agency.highlysuspect:voldeloom:2.2`)

Remapping is in a bit of a weird state, but i'm about to do massive breaking changes to the system, so this is a release cut in haste

## New

* Preliminary support for Gradle 8
  * "preliminary" as in "I don't know if it's super busted, but at least the basics work"
* The 1.3.2, 1.6.4, and 1.7.10 development environments work
  * 1.3.2 is ok but there is a wall of warnings about Minecraft being in the `net.minecraft.src` namespace, i think they're harmless(?)
  * 1.6.4 i'm a bit unsure about (just because binpatches are pretty weird to deal with) but it looks ok
  * 1.7.10 has janky unfinished MCP mappings because we are entering the MCPBot era now, and it can't read MCPBot exports just yet
* Preliminary "layered mappings system"
  * Access with `mappings volde.layered { ... }`.
  * Available commands:
    * `baseZip(Object)`, which parses the mappings through... the exact same mappings parsing system used before (look i said it was preliminary)
    * `unmapClass(String)` and `unmapClass(Collection<String>)`, which remove class mappings
      * functionally replaces `hackHackHackDontMapTheseClasses`
  * conceptually, a blank MCP mappings set is created, then each command visits it top-to-bottom
  * commands that take `Object` can accept:
    * `File` or `Path` arguments
    * `Dependency` objects from gradle
    * `String`s:
      * if it "looks like a URL" (starts with `http:/` or `https:/`), it will be automatically downloaded (to `(project dir)/.gradle/voldeloom-cache/layered-mappings/downloads`)
      * if not, it will be treated as a maven coordinate
  * computed mappings go in `(project dir)/.gradle/voldeloom-cache/layered-mappings/(hash).zip`, where `hash` is computed from the settings on each layer (ideally you shouldn't have to manually cachebust mappings when messing around with the settings)

## Changes

* Dependency on Stitch removed
  * It was used only for `JarMerger`, and i now use a modified version (that doesn't need to "remap annotations" from `Environment` to `SideOnly`)
* Removed `remapSources` and `migrateMappings` (and removed their dependencies, Mercury and `lorenz-tiny`). They might return later, but they're a maintenance burden while i prepare for the new mappings system, and there are no other mappings to migrate to other than MCP lol
* Updated fabricmc fernflower.
  * This version can use multiple CPU cores to make it even faster. I didnt implement this, blame covers i think.
  * (i made it default to processor count minus 1, so u can actually use your computer in the mean time lmao)
* The `genSourcesDecompile` and `genSourcesRemapLineNumbers` plumbing tasks have been removed and merged into the regular `genSources` task
  * this alone somehow melted away like 5 zillion lines of complexity in afterEvaluate

# 2.1 (`agency.highlysuspect:voldeloom:2.1`)

## Breaking changes

* Changed the Gradle extension's name from `minecraft { }` to `volde { }`.
  * Preparation for adding some more free-functions to the extension (it'd be weird if you referred to them with `minecraft`)
  * Possibly preparation for "getting out of the way of other extensions", so you can use it and another Minecraft plugin in the same project? Maybe?
  * Echoes what Loom did - the name is more accurate
* Moved `srgsAsFallback`.
  * Replace it with `forgeCapabilities { srgsAsFallback = true }`.
* Adjusted file paths to some intermediate products, to be more consistent:
  * Forge's libraries are downloaded to a folder containing the complete Forge artifact name, not just the version
  * Mappings have one canonical name, which also contains the complete mappings artifact name (instead of ad-hoc gluing the artifact id and version together in a slightly different way every time)
  * Mapped Minecrafts, as well as -sources and the like, are in `(cache dir)/mapped/(mappings name)/(...).jar`.
  * **(idk if this is breaking)** - Mapped Minecraft is added to the project using a file dependency instead of a `flatDir` dependency.
  * **(workspace-breaking)** - You may need to delete files using the old naming convention from `./run/coremods`.
* The hack that erased some classes from the mappings so Ears 1.4.7 would work in deobf is removed
  * Replaced with `hackHackHackDontMapTheseClasses` property inside `volde`
  * As the name suggests :tm: i will remove it later when i get the good mappings system up

## New

* Small system for "changing behavior of the plugin based on what era of Forge you're using".
  * Configure this with `volde { forgeCapabilities { /* ... */ } }`.
  * Set `distributionNamingScheme` to pick which naming scheme your mods are remapped to.
  * Set `srgsAsFallback` to assert that this Forge version maps everything to SRG names; don't set it to assert that unmapped names show through as proguarded names.
  * If you don't set them, they will be guessed from the Minecraft version and Forge version:
    * `distributionNamingScheme` is `intermediary` since 1.5 and `official` before it
    * `srgsAsFallback` is `false` since 1.5 and `true` before it
* Rudimentary custom access transformers.
  * Specify dependencies in the `accessTransformers` configuration, e.g. `accessTransformers files("./src/main/resources/my_ats.cfg")`.
  * **(currently you will need to refresh dependencies to force-update artifacts derived from it... sorry)**
  * The plugin will *not* create a Forge coremod for you that will cause ATs to be found in production - you must do this yourself.
  * The plugin will *not* retrieve ATs transitively from mod dependencies.
    * Forge has them configurable through Java in the coremod, so it's impossible in the general case.
    * Just paste them into the project. (Because I don't try to auto generate a coremod, you don't have to worry about them showing up in the built jar)
* Binpatches.
  * 1.6.4 stopped being a jarmod, instead distributing patches inside a `binpatches.pack.lzma` file.
  * Now, if this file exists, Voldeloom is able to parse the file and apply the binary patches.
  * You can't quite launch 1.6 yet, but it's a start.

## Other changes

* Rewrote how `genSources` interacts with Fernflower.
  * It does not attempt to interact with the Gradle `ProgressLogger` system anymore, because it didn't appear to be working. This also removed a *lot* of logging being swallowed by the progress logger.
  * Another optimization (rather brazenly using NIO for reading classes from the jar) improved the runtime a fair bit on my computer. This is a tinge unsafe, so `-Pvoldeloom.saferFernflower` will toggle back to the old `ZipFile`/`ZipEntry` system.
* Reduced retained size of in-memory MCP mappings by about 50%.
  * You can also reduce the retained size of the in-memory Tiny mappings tree with `-Pvoldeloom.lowMemory`, but it's a bit slow and unsafe, whcih is why it's behind a flag. Might be useful if you're very memory constrained and want to squeeze out every drop.
* Redid the whole "dependency provider" system to be less garbage.
  * Names of providers have changed. The general shape of log output also changed, a bit less spammy, but I will still spam you with file paths.
* The project-wide `offline` and `refreshDependencies` flags were moved from `Constants` into the extension:
  * Set the `voldeloom.offline` system property, project property, pass `--offline` to Gradle, or configure `offline` in the extension to configure offline mode
  * Set the `voldeloom.refreshDependencies` system property, project property, pass `--refresh-dependencies` to Gradle, or configure `refreshDependencies` in the extension to configure refreshDependencies mode
  * These are different from the global Gradle flags so that you can force Voldeloom to re-remap its derived artifacts, but not cause Gradle to do a bunch of redownloading too

# 2.0 (`agency.highlysuspect:voldeloom:2.0`)

Initial release of my fork.