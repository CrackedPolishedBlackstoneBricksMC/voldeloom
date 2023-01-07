# Voldeloom-disastertime

Gradle plugin for ~~Fabric~~ ancient versions of Forge. 

If you're interested in the history:

* Based off prerelease version of Fabric Loom 0.4 by [Loom contributors](https://github.com/TwilightFlower/fabric-loom/graphs/contributors) Aug 2016 - Jun 2020.
* Forked by [TwilightFlower](https://github.com/TwilightFlower/) May 2020 for the release of [Retro Tater](https://github.com/TwilightFlower/retro-tater); she did lots of the initial architecture work.
* Tweaked, maintained, and additional version support by [unascribed](https://github.com/unascribed/) May 2021 - May 2022 for the release of [Ears](https://git.sleeping.town/unascribed/Ears/src/branch/trunk/platform-forge-1.4) and other Forge mods.
* Minor tweak by [quaternary](https://github.com/quat1024) Jul 2022 for the release of [Hopper](https://github.com/quat1024/hoppers).
* Rewritten by quaternary Dec 2022 - Jan 2023.

This is the `disaster-time` branch, aka "quat's playground". Here be dragons. It's me. I'm the dragon.

# current status

What works:

* Forge's Maven is configured for you, replete with the `metadataSources` forward-compat hack for gradle 5+
* `genSources` decompiles the whole game, apart from ~5 methods due to MCP errata (moved switchmap classes)
  * Attaching and browsing sources works in IDEA
  * "Find usages" works too
* Asset downloading uses the old, flatter format 
* `runClient` will successfully start a copy of Minecraft Forge 1.4.7
  * Getting in-world works. It works due to a kludge, but it does work
  * Breakpoints and debugging seem to work (line #s are off in `Minecraft` due to decompiler crap but yeah)
  * The `shimForgeLibraries` task predownloads Forge's runtime-downloaded deps and places them in the location Forge expects, because the URLs hardcoded in forge are long dead
  * The `shimResources` task will copy assets from your local assets cache into the run directory (because you can't configure `--assetsDir` in this version of the game)
* Parse MCP mappings directly out of a Forge `-src` zip! No need to download MCP separately, and definitely no need to manually paste the Forge zip on top
  * See sample mod for how to do this.
  * If you still choose to download an mcp zip, when the internet archive inevitably gives you a zero-byte there's a nicer error now lol
* Partial backport of the "extendable run configs" thing from newer Fabric Loom versions
  * Define your own run configs, with custom vm args and system properties and stuff
  * 1.4 doesn't parse any program arguments apart from the username (arg 0) and session token (arg 1)
* `modCompile`/etc might even work!
  * `coremodCompile`/etc exists for coremods (they will be moved to the `coremods` folder, where Forge wants to find them) 
* Currently contains some magic kludges to get Ears working (it just doesn't expect to be ran inside deobf. Lol)

Main focus is on Gradle 7. Gradle 4 probably doesn't work yet; i'd like it to

What doesn't work yet:

* Minecraft versions other than 1.4.7 don't work
  * The long-term goal is to merge the differences between the 1.2.5/1.5.2 branches into something runtime-configurable
* Generated IDE run configs are likely broken. The `runClient` task is more of a priority.
* I don't know how broken Eclipse/VSCode are
* Source remapping tasks don't work (but who cares cause you're stuck with MCP)
* `migrateMappings` doesn't work (but who cares cause you're stuck with MCP)
* Probably a lot of other things don't work

What I'd like to fix:

* Ideally the game should be launched with a copy of java 6 or 8, right now i think gradle itself has to be running on an appropriate jdk
* I might have wandered right into this bug https://github.com/FabricMC/fabric-loom/issues/633 (even though the cause is completely different) if mezz is right I cannot believe Java is this shitty. Come on now

What I'd like to add:

* Quiltflower lol (kinda a java 11 moment though)
* Launchwrapper and/or DevLaunchInjector support would be nice
  * Possibly ship a launchwrapper injector that makes Forge, e.g. scan for coremods from the classpath instead of just that one coremods folder, dont attempt to download libraries, etc
* (if i wanna get really silly) Use Forge's secret access transformer command-line program instead of maintaining an access transformer parser

# Differences between this toolchain and period-accurate Forge

Basically this uses a more Fabricy "do as much as possible with binaries" approach. This partially owes to the project's roots in Fabric Loom, which is a completely binary-based modding toolchain, but also because it's a good idea.

* Operating at the level of whole class files, we install Forge the end-user way by downloading Minecraft, pasting the release Forge jar on top, and deleting META-INF.
* Operating inside each class file, we then apply dev-environment creature-comforts like statically applied access transformers, remapping to MCP, blah blah.
* Only *then* do we even *think* about touching Fernflower.
  * It's even optional; running `genSources` is not required to compile a mod.

Skipping Fernflower makes everything nice and snappy. One exception to this hierarchy is that we merge the client and server jars first (using FabricMC's JarMerger) and paste Forge's files on top of the merged jar, when the period-accurate installation process would probably paste Forge on top of merely a client jar or server jar. This is seamless because Forge's class-overwrites were evidently computed against a merged jar in the first place (see `in.class`, which ships a `SideOnly` annotation on a vanilla method).

Forge's period-accurate installation process is much more source-based - the game is immediately decompiled using a known Fernflower version (I think maybe some binary remapping is done using a tool called Retroguard), source-patches are applied to fill decompiler gaps + to patch in Forge's features, the rest of remapping is performed using textual find-and-replace, and the whole thing is fed back to `javac` to produce the jar you run in development. This was done using some Python 2 scripts and binaries that you'd download alongside the forge/mcp install and trigger from your Ant build.

These days we have a much more well-rounded set of class binary-manipulation tools available straight off-the-shelf, like `tiny-remapper`, `JarMerger`, Java's `ZipFileSystem`, etc, that make working with class binaries very expressive and fun. There isn't much reason to drop back to source files.

# Running sample projects

TODO: there's also atm only one sample project, for 1.4.7

There doesn't seem to be a nice way to develop a Gradle plugin and actually *use* the plugin to see if it works (no, not "write automated tests for the plugin", *actually* use it) at the same time. This is because Gradle sucks.

So: 

* Sample projects contain a line in `settings.gradle` that includes the main voldeloom project as an "included build". This feels a bit backwards because the subfolder is "including" the parent folder. It is what it is.
* In IDEA, you can right-click on each sample project's `build.gradle` and press "Link Gradle Project" towards the bottom of the dropdown. It's like how IntelliJ is able to discover subprojects and put them in the gradle tool window, but it needs a bit of manual assistance cause this isn't a subproject.
* The sample projects will then appear in the Gradle tool window for perusal. Note that the plugin will also be *compiled against* the version of Gradle used in the sample project.

Due to this cursed Gradle setup, the "root project" is not what you think the "root project" is, so run configs generate in the wrong spot. Basically you need to make a `./sample/1.4.7/.idea` directory, voldeloom will think it belongs to the root project and dump run configs into that, copypaste them back into `./.idea`, restart IDE. There's your run configs. Need to investigate this further, see if this root-not-actually-root situation happens in real projects too...

#### Debugging the plugin

Breakpoints don't work if you just hit the "refresh gradle" button, but if you select the task in the `Select Run/Debug Configuration` bar, you can press the debug button.

# Common problems for consumers

General debugging stuff:

* You must fill one dependency for *each* of the `minecraft`, `forge` and `mappings` configurations, things will explode otherwise.
* When in doubt, poke around in your Gradle cache (`~/.gradle/caches/fabric-loom`). If there are any obviously messed-up files like zero-byte files, corrupt/incomplete jars or zips, delete them and try again.
  * many of the "minecraft setup" processes are not actual Gradle tasks, so they don't benefit from gradle's correct computations ot task-uptodateness
* Run your Gradle task with `--info --stacktrace`. I tried to make the `--info`-level logging friendly.

I agree! There *should* be better error messages!

### Forge whines about getting `e04c5335922c5e457f0a7cd62c93c4a7f699f829` for a couple of dependency hashes

The `shimForgeLibraries` task is intended to download the libraries Forge wants and place them in the locations it expects to find them before launching the game, since they were removed from the hardcoded URLs in Forge a long time ago (I think that's the sha1 of the Forge server's 404 page).

Either that task didn't run and the libraries aren't there (examine the Gradle log to see if it ran), or the `minecraft.applet.TargetDirectory` system property did not get set on the client and it's trying to read libraries out of your real `.minecraft` folder - if it's doing that, the rest of the game will also try to run out of that folder.

### Ctrl-sprint doesn't work

That's just vanilla babey!! Wasn't invented yet.

### Forge NPEing about something in `FMLRelaunchLog`

Forge assumes the `.minecraft` directory exists without checking or creating it, and if an exception is thrown when making the log file it will swallow the exception, leading to nullpointers shortly after. On the client, the directory defaults to your actual `.minecraft` directory (eg, `%APPDATA%\.minecraft` on windows), but its value can be set using the `minecraft.applet.TargetDirectory` system property. On the server the directory is the pwd. So generally this comes up if you have a run config that isn't correctly shunting the `TargetDirectory` property to the game.

"`.minecraft` directory" is sort of a misnomer because the .minecraft part is just the default value tbh, it can be named whatever you want. Eg in this project it's named `run`

### Buuuunch of logspam about "Unable to read a class file correctly" or "probably a corrupt zip"

This tends to happen if anything compiled with the Java 8 class file format is on the classpath. Forge scans the entire classpath to look for mods, and uses ObjectWeb ASM 4 to do so, which fails to parse classes newer than the ones used in Java 6. It's ultimately harmless because it wasn't going to find any Forge mods inside `rt.jar` anyway.

(Not sure why this happens when using generated run configs, instead of the gradle runClient task, probably a classpath difference)

# Notes page

See `quat_notes/old notes.md` for stuff that used to be on this page but got outdated.

## Architecture (new updated early jan 2023) (working) (free download)(no virus)

The entrypoint is `LoomGradlePlugin`, which gets called upon writing the `apply plugin` line.

1. Hello log message is printed
2. `java`, `eclipse`, and `idea` plugins are applied, as if you typed `apply plugin: "eclipse"`
3. `GradleSupport.detectConfigurationNames` determines if you're on a `compile` or `implementation`-flavored version of Gradle
4. An *extension* is created, LoomGradleExtension; this is what defines the `minecraft {` block you can type some settings into. I think more recent versions call this `loom`
    * The settings are not available right away (remember, we're still on the "apply plugin" line when evaluating the script)
    * They will be available in `project.afterEvaluate` blocks, and since tasks are executed after those, in task configuration and execution
5. A couple maven repos are added, as if you typed them in to a `repositories {` block:
    * Mojang's,
    * Minecraft Forge
    * The remapped mod cache, for mod dependencies (project .gradle/loom-cache/remapped_mods)
6. Several [*configurations*](https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.Configuration.html) are created
    * `minecraft` - extends `compile`/`implementation`
        * Minecraft artifact straight off of Maven
    * `minecraftNamed` - extends `compile`/`implementation`
        * Minecraft named with your chosen mappings
    * `minecraftDependencies`
        * Minecraft's own libraries, like LWJGL
    * `mappings`
        * Mappings artifact straight off of Maven
    * `mappings_final`
        * Mappings artifact cooked to a format that tiny-remapper can parse
        * extends `compile`/`implementation`
    * `forge`
        * Forge artifact straight off of Maven
    * `forgeDependencies`
        * Forge's autodownloaded libraries, like Guava
    * and a couple for mod dependencies:
    * `modCompileClasspath`
    * `modCompileClasspathMapped`
    * `modCompile` - extends `modCompileClasspath` (and `compile` is set to extend `modCompileClasspathMapped`)
    * `modApi` - extends `modCompileClasspath` (and `api` is set to extend `modCompileClasspathMapped`)
    * `modImplementation` - extends `modCompileClasspath` (and `implementation` is set to extend `modCompileClasspathMapped`)
    * `modRuntime` (and `runtime` is set to extend `modCompileClasspathMapped`)
    * `modCompileOnly` - extends `modCompileClasspath` (and `compileOnly` is set to extend `modCompileClasspathMapped`)
7. some IntelliJ IDEA settings are configured, same stuff you could do if you wrote an `idea { }` block in the script
8. All the Gradle tasks are registered
    * migrateMappings
    * remapJar, remapSourcesJar
    * genSourcesDecompile, genSourcesRemapLineNumbers, genSources
    * downloadAssets
    * genIdeaWorkspace, genIdeaRuns, genEclipseRuns, vscode
    * (my fork) shimForgeLibraries, shimResources, copyCoremods
    * runClient, runServer
    * A large number of `cleanXxxxxProvider` tasks for each *dep provider* (more on those in a bit), and a `cleanEverything`/`cleanEverythingNotAssets` task to run them all
    * debugging task printConfigurationsPlease
9. The `idea` task is set to be `finalizedBy` the `genIdeaWorkspace` task. Similarly for `eclipse` and `genEclipseRuns`.

Then we ask for an `afterEvaluate` callback, so the following is able to access the settings configured in the `minecraft { }` block:

1. Run all the *dep providers*. These are a little system for "things that must run before task execution/dependency resolution, but depend on the values set in the `minecraft` block, so they can't run too early either" as you can see... very elegant and beautiful, not at all a kludge
    * Inspect Forge and parse its access transformers.
    * Parse a class in the Forge jar; take note of its autodownloaded dependencies.
    * Download Minecraft.
    * Download Minecraft's assets.
    * Download Minecraft's dependencies and native libraries.
    * Merge Minecraft client and server together into one merged jar.
    * Paste the Forge jar on top of the merged jar and delete META-INF.
    * Access transform the pasted jar according to Forge's access transformers.
    * Parse the mappings file.
    * Remap the AT'd jar using the mappings.
    * Remap mod dependencies (`modImplementation` etc) using the mappings.
    * Set up a DevLaunchInjector script, this is vestigial why is this still here lol, I don't even use dli now
2. Some `genSources` tasks are wired up and configured with the extension's mappings provider
3. If `extension.remapMod` is set (defaults to true), it "`// Enables the default mod remapper`". (Need to clean this up proly but haven't focused on this area yet)
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
4. Maven publication settings are configured, to forward mod dependencies into your maven POM.

## How does modCompile and friends work

`Constants.MOD_COMPILE_ENTRIES` contains a table of `RemappedConfigurationEntry`s. Recall that a Configuration is a pile of dependencies.

Each entry consists of:

* a "source configuration" like `modImplementation`
* a "target configuration" like `implementation`
* a "remapped configuration" like `modImplementationMapped`
* boolean for whether it's on the compilation classpath, here `true`
* the maven scope that it roughly maps onto (`compile`, `runtime`, or none; here `compile`)
* (my addition) whether it's a coremod config entry, here `false`

The target configuration extends from the remapped config; here `implementation` extends from `modImplementationMapped`. Also, if the entry is on the compilation classpath, the configuration named `modCompileClasspath` extends from the source configuration, and the configuration named `modCompileClasspathMapped` extends from the remapped configuration.

Developers usually interface with this system by adding mods to the source configuration, like using `modImplementation "vazkii:Botania:12345"` to declare a dependency on Botania. The expectation is that this artifact points to a mod distributed in the release namespace, and the intention is that it will be fed through the same remapping process that Minecraft was fed through, to bring it into the named namespace.

Functionally this means:
* take each of the standard gradle Java dependency scopes (`implementation`/`api`/`runtimeOnly`/`compileOnly`, and also `compile` for legacy reasons i should probaby nix)
* derive a "source configuration" from it that holds incoming modded artifacts
* derive a "remapped configuration" from it that holds remapped-to-current-namespace modded artifacts - this config is made a *part* of the standard Java dependency scope

The job of RemappedDependenciesProvider is to locate artifacts in the source configurations, remap them, and dump them into in the remapped configurations.

#### `Constants.MOD_COMPILE_ENTRIES` is touched in five places

* once in LoomGradlePlugin to set up the above relationships
* again in LoomGradlePlugin to apply their maven scopes
* once in RemappedDependenciesProvider to enumerate mods to remap
* again in RemappedDependenciesProvider to enumerate the mod remap classpath (which seems unnecessary, because isn't that what something like `modCompileClasspath` is for? i can make something like `modRemapClasspath` that unconditionally contains every source config)
* once in CopyCoremodsTask to implement the kludge where remapped coremod jars are copied into the `run/coremods` folder for Forge to find

It probably *should* be touched in `RunTask` to implement the other part of that kludge, where things in `coremods` are left off the runtime classpath. Basically a `coremodCompile` dependency will turn up on the runtime classpath due to (this is why i built the graphviz task) `RemappedDependenciesProvider` producing a version in `coremodCompileMapped`, which is extended by `implementation`, which is extended by `runtimeClasspath`, which is the task RunTask uses to enumerate mods.

### Questions and notes

So. How good is this structure.

Arguably the only important factors for a *remapped* mod are "is it on the mod compile classpath" and "does it turn up at runtime". There's two ways it can show up at runtime: either by being put on the classpath, or by being copied into the `coremods` folder for FML's coremod scanner to find.

These don't have to be defined ahead-of-time, right? The important part is a) inheritance relations are set up and b) `RemappedDependenciesProvider` can find it, but it's nothing that can't be done in, say, `LoomGradleExtension`? Modders messing with their own configurations and source-set stuff should be able to add their own remapping versions of them too.

`coremod` configs should *never* appear on things extended by `runtimeClasspath` or Forge will notice and complain about duplicate mods. This means no `runtimeOnly` and no `implementation`. If this is done, the silly hack in `RunTask` won't be needed.

For completeness's sake, (this is regular gradle stuff) the Venn diagram has `compileOnly` in the circle labeled `compileClasspath`, `runtimeOnly` in the circle labeled `runtimeClasspath`, and `implementation` in the intersection because it ends up on both classpaths. (I'm not sure where `api` fits in actually, some goofy hack is currently making it extend `implementation` in voldeloom right now)

## ?

This is mostly just a curiosity, but the dist of Forge 1.4.7 does have a jar transformer command-line application buried inside, at `cpw.mods.fml.common.asm.transformers.AccessTransformer`. I haven't tried it to see if it works. There's a `public static void main` and everything. All the details about how Forge really parses ATs are there. This is what's used at end-user runtime to access transform classes on-the-fly. Good thing: if that tool always exists, it'll always be in-line with the access transformers version used in the version of Forge. Bad thing: that's a big if.