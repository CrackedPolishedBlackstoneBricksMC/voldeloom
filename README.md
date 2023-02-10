# Voldeloom-disastertime

Gradle plugin for ~~Fabric~~ ancient versions of Forge. 

If you're interested in the history:

* Based off prerelease version of Fabric Loom 0.4 by [Loom contributors](https://github.com/TwilightFlower/fabric-loom/graphs/contributors) Aug 2016 - Jun 2020.
* Forked by [TwilightFlower](https://github.com/TwilightFlower/) May 2020 for the release of [Retro Tater](https://github.com/TwilightFlower/retro-tater); she did lots of the initial architecture work.
* Tweaked, maintained, and additional version support by [unascribed](https://github.com/unascribed/) May 2021 - May 2022 for the release of [Ears](https://git.sleeping.town/unascribed/Ears/src/branch/trunk/platform-forge-1.4) and other Forge mods.
* Minor tweak by [quaternary](https://github.com/quat1024) Jul 2022 for the release of [Hopper](https://github.com/quat1024/hoppers).

And because you're on the `disaster-time` branch:

* Rewritten by quaternary Dec 2022 - Feb 2023.

# Usage

Start with this `build.gradle`:

```groovy
buildscript {
	repositories {
		mavenCentral()
		maven { url "https://maven.fabricmc.net" }
		maven { url "https://repo.sleeping.town" }
	}
	dependencies {
		classpath "agency.highlysuspect:voldeloom:2.1-SNAPSHOT"
	}
}

apply plugin: "agency.highlysuspect.voldeloom"

java.toolchain.languageVersion = JavaLanguageVersion.of(11) //Last version able to set a --release as low as 6
compileJava.options.release.set(6) //Forge doesn't understand classes compiled to versions of the class-file format newer than Java 6's

String minecraftVersion = "1.4.7"
String forgeVersion = "1.4.7-6.6.2.534"

dependencies {
	minecraft "com.mojang:minecraft:${minecraftVersion}"
	forge "net.minecraftforge:forge:${forgeVersion}:universal@zip"
	mappings "net.minecraftforge:forge:${forgeVersion}:src@zip"
}

volde {
	//more configuration goes here...
}
```

(Infinite thanks to unascribed for hosting the maven.)

I would *strongly* suggest using modern tech (Java 17 and Gradle 7.6), but if you require legacy tech for some reason I won't judge, it should work on versions as old as Gradle 4 provided that you use Java 8.

Now `./gradlew runClient --info --stacktrace` should perform a bunch of magic culminating in a Minecraft Forge 1.4.7 client window. (Modern Gradle users shan't worry about Java versions when invoking this task, as the toolchains feature will be leveraged to provision a Java 8 environment.) As an optional next step, `genSources` will churn out a nice sources jar (with javadoc!) for you to attach in your IDE, just like Loom. Then, just start modding.

# Documentation / help

Well I've gotta finish it first! Even this `README` gets outdated worryingly quickly.

* Look in `sample` for some sample projects. These are compiled in CI, so it should at least get that far.
  * note the caveat about the weird gradle setup though, you can't just copy all files in the sample project and start working
* See the `LoomGradleExtension` class for a full list of things you can configure from `volde { }`.
* I'm trying to write lots of javadoc?
* [Ask me](https://highlysuspect.agency/discord).

If you're looking for general 1.4 Forge development advice, try [here](https://github.com/quat1024/hoppers/blob/trunk/NOTES.md).

# current status

What works:

* Forge's Maven is configured for you with free bonus `metadataSources` forward-compat magic for gradle 5+
* `genSources` (sans ~5 methods with MCP messed-up switchmaps), attaching sources in intellij, browsing and find usages, MCP comments propagating through
* Asset downloading, in the old file layout that legacy versions use
* `runClient` gets in-game (on at least 1.4.7 and 1.5.2)
  * `shimForgeLibraries` task predownloads Forge's runtime-downloaded deps and places them in the location Forge expects, because the URLs hardcoded in forge are long dead
  * `shimResources` task will copy assets from your local assets cache into the run directory (because you can't configure `--assetsDir` in this version of the game)
* Recognized mapping sources: a Forge `-src` zip, an MCP download, tinyv2 archives
* Mostly-complete backport of the "extendable run configs" thing from newer Fabric Loom versions
  * Define your own run configs, with per-config vm args and system properties and stuff
  * 1.4 doesn't parse any program arguments apart from the username (arg 0) and session token (arg 1)
* `modImplementation`/etc works
  * `coremodImplementation`/etc exists for coremods that exist at runtime, which need special handling (`remappedConfigEntryFolderCopy` task handles it) 
* Gradle 4 and 7 work 
  * On Gradle 7-, use `modCompile` instead of `modImplementation`, and drop the `only` from `modRuntimeOnly` (`implementation`/`runtimeOnly` are a gradle 7 convention)
* On Gradle 6+, a Temurin 8 toolchain is provisioned for run configs. You can configure the version and vendor
  * Done without breaking Gradle 4 source compatibilty in the plugin btw... so its kinda jank

(There are 2 magical secret mapping kludges that make Ears work, because it wasn't expected to run in a dev environment, that i will remove after adding user-configurable mappings)

What doesn't work yet:

* Minecraft versions other than 1.4.7 and 1.5.2 don't work
  * The long-term goal is to merge the differences between the 1.2.5/1.5.2 branches into something runtime-configurable
* Generated IDE run configs are broken.
  * The `runClient` task works, and is more of a priority because I get much more direct control over the startup process
  * (I don't think many modders know what the actual difference between `runClient` and ide runs are, maybe i should write something up)
  * Something is putting a million java 8 jars on the runtime classpath
* I don't know how broken Eclipse/VSCode are
* Dependency source-remapping is broken, and `migrateMappings` is broken. If you're using retro mapping projects other than MCP....... have Fun
* You can depend on other people's coremods, but you can't develop them (they don't end up in the `coremods` folder where Forge wants to find them)
* You can't depend on mods that require access transformers, and you can't write your own access transformers
* (both of the above require "projectmapping", the ability for the plugin to recognize that a configuration is per-project and shouldn't be shared into the global gradle cache)
* The jar remapper (tiny-remapper) is "too smart" - MCP used a much simpler namefinding algorithm (you might need to worry about [this sort of thing](https://github.com/unascribed/BuildCraft/commit/06dc8a89f4ea503eb7dc696395187344658cf9c1))

What I'd like to add:

* the aforementioned projectmapping
* Quiltflower lol
  * This isn't "using shiny new tech for the sake of it", it does successfully do some methods that Fernflower fails to decompile due to the switchmap comedy funny
  * Quiltflower only runs on java 11 though
* Launchwrapper? DevLaunchInjector?
* 1.6/1.7 support is blocked on needing a working Pack200 parser to read `binpatches.pack.lzma`, yes i tried commons-compress it's broken 
  * Why'd they use pack200 when there aren't even any *classes* in the pack? Anyone's guess!!!
  * Tbh I think the best way is to write some glue that uses the java8 pack200 parser, then either be on or provision a java8 toolchain, and call it
  * weeee

# Differences between this toolchain and period-accurate Forge

Basically this uses a more Fabricy "do as much as possible with binaries" approach. This partially owes to the project's roots in Fabric Loom, which is a completely binary-based modding toolchain, but also because it's a good idea

* Operating at the level of whole class files, we install Forge the end-user way by downloading Minecraft, pasting the release Forge jar on top, and deleting META-INF.
* Operating inside each class file, we then apply dev-environment creature-comforts like statically applied access transformers, remapping to MCP, blah blah.
* Only *then* do we even *think* about touching Fernflower.
  * It's even optional; running `genSources` is not required to compile a mod.

We do miss out on the occasional line-comment that Forge's source patches add, but skipping Fernflower makes everything complete very fast.

One exception to this hierarchy is that we merge the client and server jars first (using FabricMC's JarMerger) and paste Forge's files on top of the merged jar, when the period-accurate installation process would probably paste Forge on top of merely a client jar or server jar. This is seamless because Forge's class-overwrites were evidently computed against a merged jar in the first place (see `in.class`, which ships a `SideOnly` annotation on a vanilla method).

Forge's period-accurate installation process is much more source-based - the game is immediately decompiled using a known Fernflower version (I think maybe some binary remapping is done using a tool called Retroguard), source-patches are applied to fill decompiler gaps + to patch in Forge's features, the rest of remapping is performed using textual find-and-replace, and the whole thing is fed back to `javac` to produce the jar you run in development. This was done using some Python 2 scripts and binaries that you'd download alongside the forge/mcp install and trigger from your Ant build.

These days we have a much more well-rounded set of class binary-manipulation tools available straight off-the-shelf, like `tiny-remapper`, `JarMerger`, Java's `ZipFileSystem`, etc, that make working with class binaries very expressive and fun. There isn't much reason to drop back to source files.

# Running sample projects

There doesn't seem to be a nice way to develop a Gradle plugin and actually *use* the plugin to see if it works (no, not "write automated tests for the plugin", *actually* use it) at the same time. This is because Gradle sucks.

Sample projects contain a line in `settings.gradle` that includes the main voldeloom project as an "included build". This feels a bit backwards because the subfolder is "including" the parent folder. It is what it is.

Gotchas with this scheme:

* The "root project" is actually the sample project, so run configs generate in the wrong spot. Basically you need to make a `./sample/1.4.7/.idea` directory, voldeloom will think it belongs to the root project and dump run configs into that, copypaste them back into `./.idea`, restart IDE. There's your run configs. (Or use `runClient`.)
* Because of the included-build mechanism, the sample project is actually in charge of *compiling* the Gradle plugin too. So on Gradle 4 it will be compiled against the Gradle 4 api and on Gradle 7 it will be compiled against the Gradle 7 api. To avoid breaking the sample projects, if something was removed in *either* Gradle version you will need reflection to access it.

IntelliJ users can right-click on each sample project's `build.gradle` and press "Link Gradle Project", which is towards the bottom of the dropdown. The sample projects will then appear in the Gradle tool window for perusal. (It seems like code-completion in the editor uses the Gradle API that you last refreshed a project from.)

Do **not** press the "reload all gradle projects" buttons. Because of the multiple Gradle versions in play they will fail to read each other's lock files, so the gradles will stomp on each other, write to the cache at the same time, try to delete each other's output etc. Crap will end up in your Gradle cache too. Instead, to refresh projects, right-click on each sample project in the tool window you're interested in and refresh it individually. 

#### Debugging the plugin

Breakpoints don't work if you just hit the "refresh gradle" button, but if you select the task in the `Select Run/Debug Configuration` bar, you can press the debug button.

# Common problems for consumers

General debugging stuff:

* You must fill one dependency for *each* of the `minecraft`, `forge` and `mappings` configurations, things will explode otherwise.
* When in doubt, poke around in your Gradle cache (`~/.gradle/caches/fabric-loom`). If there are any obviously messed-up files like zero-byte files, corrupt/incomplete jars or zips, delete them and try again.
  * many of the "minecraft setup" processes are not actual Gradle tasks, so they don't benefit from gradle's correct computations ot task-uptodateness
* Run your Gradle task with `--info --stacktrace`. The plugin does spam `--info` with quite a bit of useful stuff.

I agree! There *should* be better error messages!

### Forge whines about getting `e04c5335922c5e457f0a7cd62c93c4a7f699f829` for a couple of dependency hashes

The `shimForgeLibraries` task is intended to download the libraries Forge wants and place them in the locations it expects to find them before launching the game, since they were removed from the hardcoded URLs in Forge a long time ago (I think that's the sha1 of the Forge server's 404 page).

Either that task didn't run and the libraries aren't there (examine the Gradle log to see if it ran), or the `minecraft.applet.TargetDirectory` system property did not get set on the client and it's trying to read libraries out of your real `.minecraft` folder - if it's doing that, the rest of the game will also try to run out of that folder.

### Forge NPEing about something in `FMLRelaunchLog`

Forge assumes the `.minecraft` directory exists without checking or creating it. If it doesn't exist, an exception will be thrown when it creates its log file, but it swallows the exception, so you get an NPE shortly after when it tries to use the log.

So this is another "the game is probably not using the correct working directory" bug. Check that the `minecraft.applet.TargetDirectory` system property is set.

### Buuuunch of logspam about "Unable to read a class file correctly" or "probably a corrupt zip"

Something compiled to Java 8's classfile format is on the classpath. Forge only works with classes compiled for Java 6.

(Not sure why this happens when using generated run configs, instead of the gradle runClient task, probably a classpath difference)

# Notes page

See `quat_notes/old notes.md` for stuff that used to be on this page but got outdated.

## Architecture (new updated early jan 2023) (working) (free download)(no virus)

**feb2023 oops might be outdated again**

The entrypoint is `LoomGradlePlugin`, which gets called upon writing the `apply plugin` line.

1. Hello log message is printed
2. `java`, `eclipse`, and `idea` plugins are applied, as if you typed `apply plugin: "eclipse"`
3. `GradleSupport.detectConfigurationNames` determines if you're on a `compile` or `implementation`-flavored version of Gradle
4. An *extension* is created, LoomGradleExtension; this is what defines the `volde {` block you can type some settings into. I think more recent versions call this `loom`
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
    * and a couple for mod dependencies. These are added in pairs, and stowed in a "remapped configuration entries" list that later tasks will use
    * `modImplementation` and `modImplementationNamed`
        * `implementation` extends `modImplementationNamed`
    * `modCompileOnly` and `modCompileOnlyNamed`
        * `compileOnly` extends `modCompileOnlyNamed`
    * `modRuntimeOnly` and `modRuntimeOnlyNamed`
        * `runtimeOnly` extends `modRuntimeOnlyNamed`
    * `modLocalRuntime` and `modLocalRuntimeNamed`
        * `runtimeOnly` extends `modLocalRuntimeNamed`
    * `coremodImplementation` and `coremodImplementationNamed`
        * `compileOnly` extends `coremodImplementationNamed`
    * `coremodRuntimeOnly` and `coremodRuntimeOnlyNamed`
        * no extensions
    * `coremodLocalRuntime` and `coremodLocalRuntimeNamed`
        * no extensions
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

Then we ask for an `afterEvaluate` callback, so the following is able to access the settings configured in the `volde { }` block:

1. Run all the *dep providers*. These are a little system for "things that must run before task execution/dependency resolution, but depend on the values set in the `minecraft` block, so they can't run too early either" as you can see... very elegant and beautiful, not at all a kludge. All these are ran one-after-the-other unconditionally
    * Download Forge.
    * Parse a class in the Forge jar; take note of its autodownloaded dependencies.
    * Download Minecraft.
    * Download Minecraft's assets.
    * Download Minecraft's dependencies and native libraries.
    * Merge Minecraft client and server together into one merged jar.
    * Paste the Forge jar on top of the merged jar and delete META-INF.
    * Parse Forge's access transformers and AT the pasted jar with them.
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