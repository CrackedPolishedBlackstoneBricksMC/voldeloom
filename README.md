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
  * If you still choose to download an mcp zip, when the internet archive inevitably gives you a zero-byte, there's a nicer error now lol
* Partial backport of the "extendable run configs" thing from newer Fabric Loom versions
  * Define your own run configs, with per-config vm args and system properties and stuff
  * 1.4 doesn't parse any program arguments apart from the username (arg 0) and session token (arg 1)
* `modImplementation`/etc works!
  * `coremodImplementation`/etc exists for coremods (they will be moved to the `coremods` folder, where Forge wants to find them) 

Magical secret kludges that make the above work:

* One mapping cheat to fix `Block$1` showing up in the wrong spot
  * Needs a fix in the mappings parser or processor or something
* Two mapping cheats to get Ears running
  * The mod remaps fine, but the release binary looks up classes by their proguarded names with `Class.forName`.
  * (Working 1.4 dev environments were a thing of the past when Ears was written, so it just doesn't expect to be ran in deobf lol.)
  * Ideally I should backport customizable per-project mappings, so you can fix stuff like this yourself.

What doesn't work yet:

* Gradle versions older than 7 (I'd like to get it working on Gradle 4, if at all possible, possibly with degraded functionality)
* Minecraft versions other than 1.4.7 don't work
  * The long-term goal is to merge the differences between the 1.2.5/1.5.2 branches into something runtime-configurable
* Generated IDE run configs are broken.
  * The `runClient` task is more of a priority because it's much easier to work with and more flexible.
  * (I don't think many modders know what the actual difference between `runClient` and ide runs are, maybe i should write something up)
* I don't know how broken Eclipse/VSCode are
* Source remapping tasks don't work (but who cares cause you're stuck with MCP)
* `migrateMappings` doesn't work (but who cares cause you're stuck with MCP)
* Probably a lot of other things don't work

To do list:

* Ideally the game should be launched with a copy of java 6 or 8, right now i think gradle itself has to be running on an appropriate jdk
* Root out "Intermediary" names from the experience where appropriate. Forge 1.4 doesn't use intermediary names except as a remapping implementation detail; unmapped methods are `a` in the live game, not `func_12345`.
  * Haven't researched when intermediary names started being shipped in real mods though, so i can't delete them all
* Write a jar remapper with a more basic "search and replace" name-finding algorithm for reobf, emulating what MCP's reobf script does (basically i want to make [this commit](https://github.com/unascribed/BuildCraft/commit/06dc8a89f4ea503eb7dc696395187344658cf9c1) not something you have to worry about)
* Investigate what's going on with the intellij run-config classpath that makes Forge try and load a bunch of java 8 jars
* You can depend on coremods with `coremodImplementation`, but you can't actually write your own, because it won't be in the coremods folder. Boo hiss.
  * Fixable with a task... possibly not fixable with a run config unless they let you run arbitrary gradle tasks
* Consider patching Forge itself on its way in to the dev workspace.
  * Upsides: Can fix the dependency downloader, can fix coremod-detection being very picky.
  * Downsides: Will need to modify the patch for every version of Forge, it's "magic" hardcoded in the gradle plugin, and Idk I like working off the original assets
* Make `--refreshDependencies` dump all cached resources
* Rebrand:tm: the project tbh.. Lol there's still a lot of user-facing references to "fabric" even

What I'd like to add:

* Propagate MCP comments into the remapped jars (I think it's possible)
* Quiltflower lol. This isn't "using shiny new tech for the sake of it", it does successfully grab some methods that Fernflower fails to decompile due to switchmap comedy. Quiltflower only runs on java 11 though
* Launchwrapper support.
  * For flexibility's sake.
  * It starts coming into the picture later in the timeline of minecraft though. Currently the game is directly launched through `net.minecraft.client.MinecraftClient#main`, which this version of Forge patches
* DevLaunchInjector????
  * I feel like it's not much of a value-add, given that all it can do is set system properties and program arguments, which i have to be able to do anyway to configure devlaunchinjector in the first place...?
  * Wait ok, so why does loom use dli then
  * Depends how shitty various IDE's run config interfaces are though
* (if i wanna get really silly) Use Forge's secret access transformer command-line program instead of maintaining an access transformer parser

# Differences between this toolchain and period-accurate Forge

Basically this uses a more Fabricy "do as much as possible with binaries" approach. This partially owes to the project's roots in Fabric Loom, which is a completely binary-based modding toolchain, but also because it's a good idea

* Operating at the level of whole class files, we install Forge the end-user way by downloading Minecraft, pasting the release Forge jar on top, and deleting META-INF.
* Operating inside each class file, we then apply dev-environment creature-comforts like statically applied access transformers, remapping to MCP, blah blah.
* Only *then* do we even *think* about touching Fernflower.
  * It's even optional; running `genSources` is not required to compile a mod.

We do miss out on the occasional line-comment that Forge's source patches add, but skipping Fernflower makes everything nice and quick.

One exception to this hierarchy is that we merge the client and server jars first (using FabricMC's JarMerger) and paste Forge's files on top of the merged jar, when the period-accurate installation process would probably paste Forge on top of merely a client jar or server jar. This is seamless because Forge's class-overwrites were evidently computed against a merged jar in the first place (see `in.class`, which ships a `SideOnly` annotation on a vanilla method).

Forge's period-accurate installation process is much more source-based - the game is immediately decompiled using a known Fernflower version (I think maybe some binary remapping is done using a tool called Retroguard), source-patches are applied to fill decompiler gaps + to patch in Forge's features, the rest of remapping is performed using textual find-and-replace, and the whole thing is fed back to `javac` to produce the jar you run in development. This was done using some Python 2 scripts and binaries that you'd download alongside the forge/mcp install and trigger from your Ant build.

These days we have a much more well-rounded set of class binary-manipulation tools available straight off-the-shelf, like `tiny-remapper`, `JarMerger`, Java's `ZipFileSystem`, etc, that make working with class binaries very expressive and fun. There isn't much reason to drop back to source files.

# Running sample projects

There doesn't seem to be a nice way to develop a Gradle plugin and actually *use* the plugin to see if it works (no, not "write automated tests for the plugin", *actually* use it) at the same time. This is because Gradle sucks.

Sample projects contain a line in `settings.gradle` that includes the main voldeloom project as an "included build". This feels a bit backwards because the subfolder is "including" the parent folder. It is what it is.

Gotchas with this scheme:

* The "root project" is actually the sample project, so run configs generate in the wrong spot. Basically you need to make a `./sample/1.4.7/.idea` directory, voldeloom will think it belongs to the root project and dump run configs into that, copypaste them back into `./.idea`, restart IDE. There's your run configs. (Or use `runClient`.)
* Because of the included-build mechanism, the sample project is actually in charge of *compiling* the Gradle plugin too. So on Gradle 4 it will be compiled against the Gradle 4 api and on Gradle 7 it will be compiled against the Gradle 7 api. To avoid breaking the sample projects, if something was removed in *either* Gradle version you will need reflection to access it.

IntelliJ users can right-click on each sample project's `build.gradle` and press "Link Gradle Project", which is towards the bottom of the dropdown. The sample projects will then appear in the Gradle tool window for perusal. Because of the multiple Gradle versions in play, it's not a good idea to use the global "reload all gradle projects" buttons, but you can right-click on each sample project in the tool window and refresh it individually. (It seems like code-completion in the editor uses the Gradle API that you last refreshed a project from.)

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
    * (my fork) shimForgeLibraries, shimResources, remappedConfigEntryFolderCopy
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

## `modImplementation` and friends

`LoomGradleExtension` contains a `NamedDomainObjectController` of `RemappedConfigurationEntry`s. A `RemappedConfigurationEntry` is a pair of configurations:

* an "input config", which the developer is intended to add release-named artifacts into (so, "obfuscated things")
* an "output config", which the `RemappedDependenciesProvider` dep provider will deposit remapped versions of the artifacts inside

and some miscellaneous functionality:

* optionally a "Maven scope" string, which declares how input artifacts get placed on the maven pom
* optionally a "copy to folder" string, which declares a custom folder that output artifacts will be copied into, inside run configs

So, let's say there's an entry with an input config of `modImplementation`, an output of `modImplementationNamed`, and a maven scope of "compile" (which there is, because `LoomGradlePlugin` adds this one by default); and you add `"vazkii:Botania:1.2.3"` to `modImplementation`.

* The dependency remapper will find your `"vazkii:Botania:1.2.3"` dependency (since it's in the input configuration), remap the artifact into the current workspace names, and add the file to the `modImplementationNamed` config (the output config).
  * Because `implementation` was also set to extend from `modImplementationNamed` in `LoomGradlePlugin`, you are able to write code against the mod in your development environment.
  * Because `implementation` extends from `runtimeClasspath` (regular gradle stuff), the mod will appear in your development runClient.
* When you publish to Maven, the POM will mention `"vazkii:Botania:1.2.3"` as a "compile" dependency.

Similar configurations exist under `modCompileOnly` and `modRuntimeOnly` (which map onto the corresponding standard Java Gradle configurations), and `modLocalRuntime` (which is the same as `modRuntimeOnly` but doesn't add to the "runtime" maven scope, intended for simply installing mods into your client workspace)

### and what is the point of the "copy to folder" feature

Forge has a limitation where it cannot load coremods from the classpath; they *must* exist in the coremods folder only. If you set `copyToFolder("coremods")` on a remapped dependency entry, a Gradle task that runs before any `runXxxx` tasks (`RemappedConfigEntryFolderCopyTask`) will notice, and copy the dependency into the coremods folder for you.

The predefined `coremodImplementation`/`coremodImplementationNamed` entry, for example, only sets `coremodImplementationNamed` to extend from `compileOnly`, not `implementation`. This means it doesn't get put on the classpath (by way of `runtimeClasspath` extending `implementation`), but Forge will pick up on it anyway, because it's in the folder.

There are `coremodImplementation`, `coremodRuntimeOnly`, and `coremodLocalRuntime` configurations predefined. `coremodCompileOnly` does *not* exist because the folder-copy workaround is only required to load the coremod *in the local development workspace*; if it existed, it would be identical to `modCompileOnly`, so just use that.

## ?

This is mostly just a curiosity, but the dist of Forge 1.4.7 does have a jar transformer command-line application buried inside, at `cpw.mods.fml.common.asm.transformers.AccessTransformer`. I haven't tried it to see if it works. There's a `public static void main` and everything. All the details about how Forge really parses ATs are there. This is what's used at end-user runtime to access transform classes on-the-fly. Good thing: if that tool always exists, it'll always be in-line with the access transformers version used in the version of Forge. Bad thing: that's a big if.