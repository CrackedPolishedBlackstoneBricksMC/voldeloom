# Voldeloom-disastertime

Gradle plugin for ~~Fabric~~ ancient versions of Forge. Based on an ancient version of Fabric Loom.

This is the `disaster-time` branch, aka "quat's playground". Here be dragons. It's me. I'm the dragon.

Also this is **not** intended to be merged upstream as-is, i.e. i'm also taking the opportunity to format stuff and clean up the code a bit, blah blah. Not trying to "change things just to change things", but also not trying to stick to upstream for the sake of keeping a small diff

# current status

What works:

* Zero-byte mcp zips now loudly explode early, instead of printing cryptic errors about a corrupt zip
* Forge's Maven is configured for you, replete with the `metadataSources` forward-compat hack for gradle 5+
* `genSources` decompiles the whole game apart from ~5 methods due to MCP errata (moved switchmap classes)
  * Attaching and browsing sources works in IDEA
  * "find usages" works too
* Setting `loaderLaunchMethod` to `launchwrapper2` causes the `runClient` task to successfully start a copy of Minecraft Forge 1.4.7
  * the `launchwrapper2` crap is temporary it will become the default
  * Breakpoints and debugging work
  * The `shimForgeClientLibraries` task predownloads Forge's runtime-downloaded deps and places them in the location Forge expects, because the URLs hardcoded in forge are long dead
Main focus is on Gradle 7. Gradle 4 probably doesn't work yet

What doesn't work yet:

* Minecraft versions other than 1.4.7 don't work
  * the long-term goal is to merge the differences between the 1.2.5/1.5.2 branches into something runtime-configurable
  * but the short-term goal is to make 1.4.7 work!
* Actually getting in to a world doesn't work (it's more switchmap crap, `Block.canSustainPlant` noclassdefs)
* The standalone server doesn't work yet
* Run configs (as opposed to using the runClient gradle task) are broken
* Asset index is broken (i don't think 1.4 supported changing the asset index?) -> no sound and 1 trillion failed s3 requests in the log
* I snipped out a thing that replaced the stock `SideOnly` annotations with `Environment` ones, but there's still Environment leftovers for some reason
* I don't know how broken Eclipse is
* `modCompile` configurations and friends probably don't work, but you knew that already
* Probably a lot of other things don't work

What I'd like to fix:

* Fix remapping exploding when packages.csv is missing
* An in-gradle method of downloading MCP and applying the forge access transformers, instead of that funny shit in the buildscript
* still lots of leftovers from e.g. DevLaunchInjector (which is not used), mixin, jar-in-jar, unused launch methods etc etc that does not apply to forge at all
  * I could go scorched-earth on this stuff but it might be handy to keep around if someone does a cursed "fabric on top of forge" project? I guess?
* Launchwrapper needs a little assistance getting ASM on the classpath
* There's a goofy ahh hack in `AbstractRunTask` to filter out someone's Guava dependency, cause it conflicts with the one Forge needs, and crashes
* Ideally the game should be launched with a copy of java 6 or 8, right now i think gradle itself has to be running on an appropriate jdk

What I'd like to add:

* Quiltflower lol (kinda a java 11 moment though)
* Backport the much nicer run-config stuff from newer versions of Loom (multiple run configs, run configs in subprojects, less hardcoded arguments for run configs, etc)
* Possibly do a custom launchwrapper tweaker, will be optional

## Sample projects

TODO: there's also atm only one sample project, for 1.4.7

There doesn't seem to be a nice way to develop a Gradle plugin and actually *use* the plugin to see if it works (no, not "write automated tests for the plugin", *actually* use it) at the same time. This is because Gradle is a terrible piece of shit.

Apparently the actual recommended way to do this is a) shove everything in `buildSrc` because imagine actually wanting to publish your plugin lol, or b) literally just publishing it to `mavenLocal` any time you do anything, and i guess ??hope the change gets picked up?? when you import the plugin by-name from an unrelated test project? Both of these are fucking ridiculous, imo.

So: 

* Sample projects contain a line in `settings.gradle` that includes the main voldeloom project as an "included build". Note that this feels a bit backwards because the subfolder is "including" the parent folder. It is what it is.
* In IDEA, you can right-click on each sample project's `build.gradle` and press "Link Gradle Project" towards the bottom of the dropdown. It's like how IntelliJ is able to discover subprojects and put them in the gradle tool window, but it needs a bit of manual assistance cause this isn't a subproject. Then you get gradle IDE integration. Works better than I expect it to, in this obvious nightmare scenario.
* Note that the plugin will be *compiled against* the version of Gradle used in the sample project. I had to blindly rewrite some legacy-handling code to use reflection because the method was removed. Will see what I can do.

Due to this cursed Gradle setup, the "root project" is not actually the "root project" and run configs generate in the wrong spot. Basically you need to make a `sample/1.4.7/.idea` directory, voldeloom will think it belongs to the root project and dump run configs into that, copypaste them back into `./.idea`, restart. There's your run configs.

Need to investigate this further, see if this root-not-actually-root situation happens in real projects too... probably need to backport some of the more modern fabric-loom run config stuff if I can...

## Debugging the plugin

~~idk lol. Println.~~ Breakpoints seem to work now? I don't think breakpoints work if you hit the "refresh gradle" button, but debugging tasks like `clean` works and can hit breakpoints inside the gradle plugin

## Common problems for consumers

*Weird NPEs when only sketching in the build.gradle:* Currently it crashes if a set of mappings is not defined. Ideally it should skip mappings-related tasks instead, or use some kind of passthrough mappings instead of `null`. Hm.

*`Failed to provide null:unspecified:null : conf/packages.csv`:* Mapping parsing, jar remapping, or something else in that area blew up. If this failed due to missing `packages.csv`, use a mcp zip merged with a forge zip. want to fix that.

# Just taking notes

How far is it safe to diverge from Loom's practices? I mean obviously there's no point in trying to keep up with upstream changes in Loom patch-for-patch, we're way too far gone by this point. Some stuff is the way it is due to this project's history as a cursed frankenstein plugin that was taped together, other stuff is the way it is for a good reason.

Same for "removing remnants of fabric", it's very possible that someone could make some "fabric loader on top of forge" bullshit and i don't want to close the door to that right away lolll

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

## Flow through the system

~~`LoomGradlePlugin` is the entry point when you call `apply plugin`. It's split across `AbstractPlugin` and that class, for some reason. AbstractPlugin happens first so i will document that~~ I removed AbstractPlugin and merged the classes

1. Hello log message is printed
2. `java`, `eclipse`, and `idea` plugins are applied (for some reason), as if you typed `apply plugin: "eclipse"`
3. (my fork) `GradleSupport.detectConfigurationNames` determines if you're on a `compile` or `implementation`-flavored version of Gradle
4. An *extension* is created, LoomGradleExtension; this is what defines the `minecraft {` block you can type some settings into. I think more recent versions call this `loom`
   * The settings are not available right away (remember, we're still on the "apply plugin" line when evaluating the script)
   * They will be available in a `project.afterEvaluate` block, or during task execution
5. A couple maven repos are added, as if you typed them in to a `repositories {` block:
   * Mojang's,
   * (my fork) Minecraft Forge
   * (my fork) Remapped mod cache, for mod dependencies (project .gradle/loom-cache/remapped_mods)
     * (happens in afterEvaluate in the original)
6. Several [*configurations*](https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.Configuration.html) are created
   * `modCompileClasspath`
   * `modCompileClasspathMapped`, extends `annotationProcessor` if `!idea.sync.active`
   * `minecraft` - extends `compile`/`implementation`
     * minecraft artifact straight from mojang's servers I think
   * `minecraftNamed` - extends `compile`/`implementation`, extends `annotationProcessor` if `!idea.sync.active`
     * minecraft named with your chosen mappings
   * `minecraftDependencies`
   * ~~`include`~~
     * ~~jar in jar stuff~~
   * `mappings`
     * the "raw" mappings artifact
   * `mappings_final`
     * mappings artifact cooked to a format that tiny-remapper can parse? 
     * extends `compile`/`implementation`, extends `annotationProcessor` if `!idea.sync.active`
   * `forge`
     * either this is the "raw" forge artifact or something else idk how it gets merged with regular minecraft
     * hmmmmmm
   * and a couple for mod dependencies:
   * `modCompile` - extends `modCompileClasspath`
     * and `compile` is set to extend `modCompileClasspathMapped`
   * `modApi` - extends `modCompileClasspath`
     * and `api` is set to extend `modCompileClasspathMapped`
   * `modImplementation` - extends `modCompileClasspath`
     * and `implementation` is set to extend `modCompileClasspathMapped`
   * `modRuntime`
     * and `runtime` is set to extend `modCompileClasspathMapped`
   * `modCompileOnly` - extends `modCompileClasspath`
     * and `compileOnly` is set to extend `modCompileClasspathMapped`
7. ~~some mixin annotation processor stuff happens, project is scanned for java compile tasks and mixin ap arguments are added~~ removed
8. some IntelliJ IDEA settings are configured, same stuff you could do if you wrote an `idea { }` block in the script
9. ~~for Some Reason the Javadoc classpath is set to the main compile classpath?~~
   * I think this is like "semi opinionated gradle magic" that has nothing to do with mods lol
   * Commented it out
10. ~~If `!idea.sync.active`, `fabric_mixin_compile_extensions` is added as an `annotationProcessor` dependency~~ removed
11. All the Gradle tasks are registered
    * cleanLoomBinaries, cleanLoomMappings, cleanLoom
    * migrateMappings
    * remapJar, remapSourcesJar
    * genSourcesDecompile, genSourcesRemapLineNumbers, genSources
    * downloadAssets
    * genIdeaWorkspace, genEclipseRuns, vscode
    * (my fork) shimForgeClientLibraries
    * runClient, runServer

Then we ask for an `afterEvaluate` callback, so the following is able to access the settings configured in the `minecraft { }` block:

1. A `LoomDependencyManager` is created and added to the `LoomGradleExtension`.
    * A `ForgeProvider`, `MinecraftProvider`, `MappingsProvider`, and `LaunchProvider` are added to the dependency manager.
    * `handleDependencies` is called on the dependency manager.
    * TODO: looks like a rabbit hole, study further
2. Some `genSources` tasks are wired up and configured with the extension's mappings provider
3. ~~The same Mixin annotation processor arguments are added to the Scala compilation task, if it exists~~ removed
4. ~~A couple more Maven repos are glued on? (Why now?)~~
   * ~~FabricMC's, Mojang's (again), Maven Central, before i removed it in my fork even JCenter.~~ Removed
   * ~~A `flatDir` maven repo is also added for the directories `UserLocalCacheFiles` (under the root project's `build/loom-cache` dir) and `UserLocalRemappedMods` (`.gradle/loom-cache/remapped_mods`)~~ Moved up
5. The `idea` task is set to be `finalizedBy` the `genIdeaWorkspace` task. Similarly for `eclipse` and `genEclipseRuns`. (Why here? Idk)
6. If `extension.autoGenIDERuns` is set (defaults to true) and this is the root project, a static helper in the `SetupIntellijRunConfigs` class is called to poop out files in `.idea/runConfigurations`
7. If `extension.remapMod` is set (defaults to true), it "`// Enables the default mod remapper`".
   * The `jar` and `remapJar` tasks are located.
   * If `remapJar` doesn't have an input:
     * `jar` is set to a classifier of `"dev"` and `remapJar` is set to a classifier of `""`.
     * `remapJar`'s input is set to `jar`'s output (using a kind of janky deprecated-in-gradle7 method, `getArchivePath`)
   * The Loom gradle extension gets `addUnmappedMod` called on it, set to `jar`'s output.
   * `remapJar`'s addNestedDependencies is set to `true`.
   * `remapJar`'s output is registered to the `archives` artifact configuration.
   * `remapJar` is set to depend on `jar`.
   * `build` is set to depend on `remapJar`.
   * `remapSourcesJar` is configured; `build` depends on it, and it depends on `sourcesJar`.
8. Maven publication settings are configured. Don't know exactly what this is about, something about including dependencies from the `modCompile`-etc configurations into the POM.

## RelaunchLibraryManager

Including this SHA-1 hash `e04c5335922c5e457f0a7cd62c93c4a7f699f829` might make this page show up on Google. TODO: write a blog post explaining this too, including the same hash.

Forge for 1.4 downloads additional library jars at *runtime*, using hardcoded URLs, for some God forsaken reason. These download URLs have long since been taken off the air. I'm hearing that putting the URLs given in the error log into the Wayback Machine gives hits, so if you're showing up here from Google, you can do that. Frustratingly the log message doesn't print where the files are expected to go: it's `.minecraft/libs`. `.minecraft` is in a platform-dependent location; on Windows it's under `%APPDATA%` (just type that into windows explorer including the percent signs).

As a launcher developer, though, I'd like to shim this so it's not an issue. (The actual system is that *any* Forge coremod can download libraries from any URL provided in the jar, btw)

The provenance of the file path:

* `RelaunchLibraryManager.performDownload` parameter `target`
* `target` comes from `RelaunchLibraryManager.downloadFile` parameter `libFile`
* `libFile` comes from `RelaunchLibraryManager.handleLaunch` local `libDir` + targFileName (the file name)
* `libDir` is set from taking parameter `mcDir` and appending a `lib` folder
* `mcDir` comes from `FMLRelauncher.setupHome` parameter `minecraftHome`
* `setupHome` might be called from `FMLRelauncher.relaunchApplet` or `relaunchClient`, which calls `FMLRelauncher.computeExistingClientHome`:
  * if the system property `minecraft.applet.TargetDirectory` is set:
    * `/` is replaced with `File.separatorChar` in the value of the property
    * `Minecraft.minecraftDir` is reflectively set to the result
  * `getMinecraftDir` is called reflectively. The result is ignored
    * If `minecraft.minecraftDir == null`, `getAppDir` is called to compute the value
      * This method fails to decompile (see quat_notes/getappdir) but it basically finds the `.minecraft` directory
  * The value of `Minecraft.minecraftDir` is read reflectively, returned, and in both cases is passed directly to `setupHome`
* Or `setupHome` might be called from `FMLRelauncher.setupServer`:
  * The hardcoded path `.` is passed to `setupHome`

In summary:

* On the client, the library path less the `/lib` suffix can be controlled with the `minecraft.applet.TargetDirectory` system property.
* On the server, the path is the current directory plus the `/lib` suffix.

To shim the library downloading process, we need to guess the directory or control it. I think it makes sense to control the `.minecraft` directory to be inside the run directory. So we can download libraries there.

Note: if `minecraft.applet.TargetDirectory` doesn't exist, Forge will NPE about logging due to a swallowed exception in `FMLRelaunchLog.<init>`.

A good resource for other downloaded libraries used by other versions of Forge -> https://github.com/PrismLauncher/PrismLauncher/blob/develop/launcher/minecraft/VersionFilterData.cpp and `https://files.prismlauncher.org/fmllibs/` acts as a mirror service too

### but wait there's more

Launchwrapper! Launchwrapper is a thing! If you use `VanillaTweakInjector` you get a new `--gameDir` argument, which can be set to any path you want and acts the same as setting the `minecraft.applet.TargetDirectory` flag.

## What is a DependencyProvider?

This is a small framework for programatically adding derived dependencies to the project in afterEvaluate. By "derived", I mean that the contents of the dependency varies based on other things in the project, such as the mapped version of Minecraft being derived from the Minecraft version and mappings version.

This is pretty much where the Magic:tm: happens - it's why you're able to dep on "minecraft remapped to MCP" when only "minecraft" and "MCP" exist as off-the-shelf artifacts.

Implementations are all over the place, but by convention the `provide` method does two related things:

1. Do a computation that relies on other real dependencies being resolved
   * such as LaunchProvider writing a fabric dev-launch-injector script to project `.gradle/loom-cache/launch.cfg`
   * or MinecraftMappedProvider combining the minecraft artifact & mappings artifact using tiny-remapper
2. Call `addDependency` to add the relevant dependency to the project
   * LaunchProvider adds dev-launch-injector to the `runtimeOnly` configuration, because the file is not useful without dli
   * MinecraftMappedProvider adds a `flatDir` repo containing the remapped minecraft file, then adds the artifact to the `minecraftNamed` configuration

These generally roll their own caching system, i.e. MinecraftMappedProvider checks to see if the file exists before calling into tiny-remapper the old-school way, with `File.exists`.

`getTargetConfig` is a configuration relevant to the DependencyProvider, but not necessarily the one that actually ends up with the dependency in it (e.g. LaunchProvider writes to `runtimeOnly` but is actually says `minecraftNamed` is its target config)

All this comes to a head in `LoomDependencyManager`, which is a part of `LoomGradleExtensions` and is configured at the beginning of the afterEvaluate block. `addProvider` stores them into a list, and the control flow is extremely complex for some reason but I think `provide` gets called on each one in turn?

The `Consumer<Runnable> postPopulationScheduler` argument accepts things to run after handling all those dependencies. It goes unused in Voldeloom.

Also, watch out for things that *look* like providers but are actually ad-hoc utilty classes, like `MinecraftLibraryProvider`.

`DependencyProvider.DependencyInfo` is a wrapper around an artifact provided by a configuration. It mainly wraps `configuration.resolve`, with some convenience methods for asserting a configuration will only ever contain one dependency no more no less (like `forge`, you shouldn't have two `forge`s), and for trying to excavate some group/name/version data out of filenames and mod files when non-Maven artifacts are in the configuration.

## LoomDependencyManager#handleDependencies

Deeply magical method

this happens when "setting up loom dependencies" is logged. cant stand how terse loom's stock logging is sometimes lol

* all registered dep providers (so, ForgeProvider, MinecraftProvider, MappingsProvider, and LaunchProvider) are sorted into categories, based off of their target configuration (they happen to be `forge`, `minecraft`, `mappings`, and `minecraftNamed` respectively)
  * the ProviderList system can handle more than one provider per configuration, but this feature happens to go unused
* the provider for the `mappings` configuration is stowed off to the side 
* for each category of dep providers:
  * its configuration is retrieved (`forge` `minecraft` `mappings` `minecraftNamed`)
  * for each artifact in its configuration:
    * a `DependencyInfo` is created for the artifact, actually this happens deeper-in but you can do a lil loop invariant motion
    * for each dep provider in the category (only one):
      * `provide` is called, with the dependency info, and a handle to add things to `afterTasks` (unused)
* retrieve the `mappings` configuration stored off to the side
  * call `ModCompileRemapper.remapDependencies` another one of those giant magic methods
* run all `afterTasks` (none in voldeloom, apart from remapDependencies for source remapping stuff)

## what each provider actually does

What's funny is that there's actually dependencies *between* providers too, but there isn't actually a system for that, providers just call `provide` on each other. Then the provider scheduler might run the provider again and it'd just see that it already output something and fail.

### `ForgeProvider`

* creates a new `ForgeATConfig` object
* resolves the `forge` dependency and asserts it contains one file
* sets `forgeVersion` to the version of that dep
* roots around in the forge jar, looking for `fml_at.cfg` and `forge_at.cfg` and passes them to `atConfig`
* doesn't do anything else yet!

these at.cfg files are in official names (the proguarded ones) so they need to be remapped, and that's what the (freestanding) `mapForge` method does. it rings up the `LoomGradleExtension` `mappingsProvider` and remaps the access transformers

all the magic happens in the `ForgeATConfig` object which is really Neat java code im too dumb for

### `MinecraftProvider`

interesting: whatever files are actually provided by your `minecraft` dep in your buildscript, just isn't looked at. only the version is looked at and used to index the vanilla version_manifest.json

* sets `minecraftVersion` to the version of the minecraft dep
* sets `minecraftJarStuff` to that plus "-forge-" and the version of the forge dep (yeah)
* sets five file paths, `minecraftJson`/`clientJar`/`serverJar`/`mergedJar`/`patchedMergedJar`, all in the global user gradle cache
* downloads the mc json and version manifest files if they don't exist yet
  * this is the minecraft-blahblah-info.json you see in your loom-cache folder inyour global user gradle cache
* parses it with gson into `versionInfo` field
* (commented this out in my fork) adds Loom itself as a compileOnly dep to your project???? hoh?? what does this have to do with Anything
* downloads minecraft-blahblah-client and minecraft-blahblah-server jars
* creates a new `MinecraftLibraryProvider`, stashes it in a field, and calls `provide` on it
  * this is not a real dep provider it's just named the same way
  * basically it parses out each of the Maven dependencies for this version (other than natives) and sticks them as real dependencies in your project's `minecraftDependencies` configuration, as well as keeping a reference to them for later use by remappers and other shit that wants it on the classpath 
* if the patched merged jar doesn't exist:
  * if the merged jar doesn't exist:
    * merge client and server jars using FabricMC Stitch
    * if it fails, the client and server jars are deleted (so i guess it assumes the merging process only fails due to corrupt zips)
  * copies `minecraftMergedJar` to `minecraftPatchedMergedJar` and patches it in-place with `ForgePatchApplier` (this seems like a bad idea)

now `ForgePatchApplier` isn't a dep provider either but i think it's important to explore what it does... and stop me if this sounds familiar

* locates the `ForgeProvider`, blindly assumes it's already ran (uh oh) and grabs the forge jar
* opens the minecraft jar
* copies all files in the forge jar into the minecraft jar
* deletes META-INF

yep! it does that

### `MappingsProvider`

* gets the minecraft provider and assumes it's already ran
  * this is real "we have a task graph at home" moments
* this is the one that logs "setting up mappings"
* resolves the artifact specified in `mappings` in your buildscript (into `mappingsJar`)
* does some horrendous bullshit string mangling i have no idea what for lol :cowboy:
* *tries* to figure out "tinyMappings" and "tinyMappingsJar" filenames by replacing `.jar` suffixes but, uhhh if the mappings don't end in `.jar` it leaves the filename the same lol
* if `tinyMappings` doesn't exist:
  * opens `mappingsJar`, which i guess in this case is actually an mcp *zip*
  * it does a lot of strange MCP munging that i should probably research how it works next
  * but basically it reads several cfg files out of the `mappingsJar` and hands them to a couple of dedicated mcp-parser classes in voldeloom
  * `fields.csv` and `methods.csv` also enter the picture somehow
  * once everything is loaded and parsed, a `TinyWriter3Column` is used to write `tinyMappings`
* `tinyMappingsJar` is created by simply packing `tinyMappings` into a jar with the file placed at `mappings/mappings.tiny`, i guess this is for tiny-remapper bullshit
* `tinyMappingsJar` is added to `mappingsFinal` configuration

now that that's done, this (of all places! here!) is where `JarProcessorManager` is initialized. ok another rabbit hole to research later

MappingsProvider is also the holder of the "mapped provider" a k a the holder for minecraft-mapped-to-mcp-names

### `LaunchProvider`

comparatively this one is very very simple, it just writes the `launch.cfg` file for dev-launch-injector and adds DLI as a dep to your project (but i commented it out)

### `MinecraftMappedProvider`

assumes the mappings provider and minecraft provider have already ran

slaps em together with tiny-remapper and adds it to the project classpath... or does it?

#### `MinecraftProcessedProvider`

*extends* MinecraftMappedProvider. the goal of this is to fix up the minecraft jar by running it through "jar processors"

## jar processor manager. how deep does it go

well in voldeloom there are only two. and there's *always* two, so it always ends up printing "using project based jar storage"

* `CursedLibDeleterProcessor` - strips `argo.` and `org.` classes out of the jar
* `ASMFixesProcessor`
  * *finally* calls `mapForge` on the forge provider
  * applies the newly-remapped forge access transformers to the jar

## the impression i get after being tail deep in these "library providers" and "jar processors" and whatnot

Why weren't these written as tasks? Probably cause its too late to add dependencies when task execution begins. So you need some sort of franken system that happens in `afterEvaluate`

I feel like this system either *wants* to be backed by a task graph, or *wants* to be simplified into straight-shot control flow, because right now it's some mixture of both where it's technically broken up into task-like pieces, but they actually have an ordering anyway

## ?

what is a "fabric installer json?" (LoomDependencyManager) probably something to do with run configs

* Nope. It's this type of file https://github.com/FabricMC/fabric-loader/blob/master/src/main/resources/fabric-installer.json and lists dependencies required for Fabric Loader itself. (Asm, tiny-mappings-parser, etc). It also stores the mainClass information for the Knot relauncher
* Its primary purpose is ofcourse in the fabric installer graphical application, it downloads fabric-loader, peeks inside, then queries this file to find the rest of things it needs.
* Loom pokes around through the project dependencies looking for a jar with this file in it, iterates through the json dependencies, and adds them as real Gradle dependencies
  * Yeah its a mess

`ModCompileRemapper` specifically looks for fabric mods, i think this has to do with mod dependencies

It's probably safe to delete instances of jij stuff because Forge does not natively support nested jars and it's really not necessary to hack that on with a mod

abstractdecompiletask uses a "line map file"

~~Forge seems to depend on ASM but that dependency is being lost along the way, possibly (at least, i see red errors in the genSources jar)~~ goddamn autodownloaded dependencies lol

investigate `MinecraftLibraryProvider`, that libraries folder doesnt seem to exist...?
* This class is really weird actually. It has a Collection<File> thats probably supposed to contain library paths, but it's never written to. DOes this break anything? If so, what

MinecraftMappedProvider calls MapJarsTiny:

* works in global gradle cache `.gradle/caches/fabric-loom/`
* source was `minecraft-1.4.7-forge-1.4.7-6.6.2.534-merged.jar`
* outputMapped ("named") is `1.4.7-forge-1.4.7-6.6.2.534-mapped-null.unspecified-1.0/minecraft-1.4.7-forge-1.4.7-6.6.2.534-mapped-null.unspecified-1.0.jar`
  * contains mcp names
* outputIntermediary ("intermediary") is `minecraft-1.4.7-forge-1.4.7-6.6.2.534-intermediary-null.unspecified-1.0.jar`
  * contains SRG names, good ol field_1234_a
  * (recall that srg classes == mcp classes unlike Yarn)