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

Due to this cursed Gradle setup, the "root project" is not actually the "root project" and run configs generate in the wrong spot. Basically you need to make a `sample/1.4.7/.idea` directory, voldeloom will think it belongs to the root project and dump run configs into that, copypaste them back into `./.idea`, restart. There's your run configs.

Need to investigate this further, see if this root-not-actually-root situation happens in real projects too... probably need to backport some of the more modern fabric-loom run config stuff if I can...

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

## Flow through the system

`LoomGradlePlugin` is the entry point when you call `apply plugin`. It's split across `AbstractPlugin` and that class, for some reason. AbstractPlugin happens first so i will document that

* Log message is printed
* `java`, `eclipse`, and `idea` plugins are applied (for some reason), as if you typed `apply plugin: "eclipse"`
* (my fork) `GradleSupport.detectConfigurationNames` determines if you're on a `compile` or `implementation`-flavored version of Gradle
* An *extension* is created, LoomGradleExtension; this is what defines the `minecraft {` block you can type some settings into. I think more recent versions call this `loom`
* A couple maven repos are added, as if you typed them in to a `repositories {` block (Mojang's, and (my fork) Minecraft Forge)
* Several [*configurations*](https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.Configuration.html) are created
  * `modCompileClasspath`
  * `modCompileClasspathMapped`, extends `annotationProcessor` if `!idea.sync.active`
  * `minecraftNamed` - extends `compile`/`implementation`, extends `annotationProcessor` if `!idea.sync.active`
  * `minecraftDependencies`
  * `minecraft` - extends `compile`/`implementation`
  * `include`
    * for jar in jar stuff
  * `mappings`
    * for the mappings version
  * `mappings_final`
    * extends `compile`/`implementation`, extends `annotationProcessor` if `!idea.sync.active`
  * `forge` for voldeloom cursed forge stuff
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

`configureIDEs` is called to configure IntelliJ IDEA:
* adds `.gradle`, `.build`, `.idea`, and `out` to the IDEA module's exclusion dirs
* configures the IDEA module to download javadoc and sources
* configures `inheritOutputDirs` (see [here](https://github.com/gradle/gradle/blob/c5a095b265396cd4ee498ff71ddece098a3b7c73/subprojects/ide/src/main/java/org/gradle/plugins/ide/idea/model/IdeaModule.java#L437-L449)?)
  * I thiiiiink this makes it so you don't need to reteach intellij that yes, you would like build artifacts in the `build/` directory please? (intellij users will know whats up)

`configureCompile` is called to configure javac. It uses [JavaPluginConvention](https://github.com/gradle/gradle/blob/c5a095b265396cd4ee498ff71ddece098a3b7c73/subprojects/plugins/src/main/java/org/gradle/api/plugins/JavaPluginConvention.java), which is apparently deprecated as of gradle 7, but when Loom was written it used gradle 4 where it was not deprecated.

I believe it locates the same thing that you locate when you write a `java {` block in your gradle.

* Sets the Javadoc classpath to include the main source set's output plus the main source set's compilation classpath. Why is this done, who knows.
* If `!idea.sync.active`, `fabric_mixin_compile_extensions` is added to the annotation processor list.
* In an `afterEvaluate` block:
  * A billion maven repos are added for some reason. FabricMC's, Mojang's (again), Maven Central, before i removed it in my fork even JCenter.
  * A `flatDir` maven repo is also added for the directories `UserLocalCacheFiles` (under the root project's `build/loom-cache` dir) and `UserLocalRemappedMods` (`.gradle/loom-cache/remapped_mods`).
  * A `LoomDependencyManager` is created and added to the `LoomGradleExtension`.
  * A `ForgeProvider`, `MinecraftProvider`, `MappingsProvider`, and `LaunchProvider` are added to the dependency manager.
  * `handleDependencies` is called on the dependency manager.
    * TODO: looks like a rabbit hole, study further
  * The `idea` task is set to be `finalizedBy` the `genIdeaWorkspace` task. Similarly for `eclipse` and `genEclipseRuns`.
  * If `extension.autoGenIDERuns` is set (defaults to true), a static helper in the `SetupIntellijRunConfigs` class is called to do that.
  * If `extension.remapMod` is set (defaults to true), it "`// Enables the default mod remapper`".
    * The `jar` and `remapJar` tasks are located.
    * If `remapJar` doesn't have an input:
      * `jar` is set to a classifier of `"dev"` and `remapJar` is set to a classifier of `""`.
      * `remapJar`'s input is set to `jar`'s output (using a kind of janky deprecated-in-gradle7 method, `getArchivePath`)
    * The Loom gradle extension gets `addUnmappedMod` called on it, set to `jar`'s output.
    * `remapJar`'s addNestedDependencies is set to `true`.
    * `remapJar`'s output is registered to the `archives` artifact configuration.
    * `remapJar` is set to depend on `jar`.
    * `build` is set to depend on `remapJar`.
    * A small kludge (relating to `addNestedDependencies`) is executed which ensures they're built first. Or something, idk
    * `remapSourcesJar` is configured; `build` depends on it, and it depends on `sourcesJar`.

Finally we are out of `AbstractPlugin`, but work continues in `LoomGradlePlugin`:

* Several executable tasks are registered:
  * `cleanLoomBinaries`, `cleanLoomMappings`, `cleanLoom` (which does both)
  * `migrateMappings`
  * `remapJar`
  * `genSourcesDecompile`, `genSourcesRemapLineNumbers`, `genSources`
  * `downloadAssets`
  * `genIdeaWorkspace`, `genEclipseRuns`, `vscode`
  * `remapSourcesJar`
  * and finally, `runClient` and `runServer`

Also a big afterEvaluate block is registered to the project:

* Dependencies are set between `genSources` and the other tasks (idk why here)
* `genSourcesDecompile`'s input and output files are configured, which includes mappings
* `genSourcesRemapLineNumbers`'s outputs are set as well
* `genSources` is set to copy `xxx-linemapped` over to the place where the project mappings provider expects it (lol)

## RelaunchLibraryManager

Including this SHA-1 hash `e04c5335922c5e457f0a7cd62c93c4a7f699f829` might make this page show up on Google. TODO: write a blog post explaining this too, including the same hash.

Forge for 1.4 downloads additional library jars at *runtime*, using hardcoded URLs, for some God forsaken reason. These download URLs have long since been taken off the air. I'm hearing that putting the URLs given in the error log into the Wayback Machine gives hits, so if you're showing up here from Google, you can do that. Frustratingly the log message doesn't print where the files are expected to go: it's `.minecraft/libs`. `.minecraft` is in a platform-dependent location; on Windows it's under `%APPDATA%` (just type that into windows explorer including the percent signs).

As a launcher developer, though, I'd like to shim this so it's not an issue. (The actual system is that *any* Forge coremod can download libraries from any URL provided in the jar, btw.)

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

Note: if `minecraft.applet.TargetDirectory` doesn't exist Forge will NPE about logging, due to a swallowed exception in `FMLRelaunchLog.<init>`

A good resource for other library versions: https://github.com/PrismLauncher/PrismLauncher/blob/develop/launcher/minecraft/VersionFilterData.cpp

### but wait there's more

Launchwrapper! Launchwrapper is a thing! If you use `VanillaTweakInjector` you get a new `--gameDir` argument, which can be set to any path you want and acts the same as setting the `minecraft.applet.TargetDirectory` flag.

## ?

what is a fabric installer json? (LoomDependencyManager) probably something to do with run configs

`ModCompileRemapper` specifically looks for fabric mods, i think this has to do with mod dependencies

It's probably safe to delete instances of jij stuff because Forge does not natively support nested jars

abstractdecompiletask uses a "line map file"

Forge seems to depend on ASM but that dependency is being lost along the way, possibly (at least, i see red errors in the genSources jar)