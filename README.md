# Voldeloom-disastertime

Gradle plugin for ~~Fabric~~ ancient versions of Forge.

* **Latest stable**: `2.4`
* **Latest snapshot**: `2.5-SNAPSHOT` (from CI every commit)

## History

* Based off prerelease version of Fabric Loom 0.4 by [Loom contributors](https://github.com/TwilightFlower/fabric-loom/graphs/contributors) Aug 2016 - Jun 2020.
* [Forked](https://github.com/TwilightFlower/fabric-loom/) by [TwilightFlower](https://github.com/TwilightFlower/) May 2020 for the release of [Retro Tater](https://github.com/TwilightFlower/retro-tater); she did lots of the initial architecture work.
* [Tweaked, maintained, and additional version support](https://github.com/unascribed/voldeloom/) by [unascribed](https://github.com/unascribed/) May 2021 - May 2022 for the release of [Ears](https://git.sleeping.town/unascribed/Ears/src/branch/trunk/platform-forge-1.4) and other Forge mods.
* Minor tweak by [quaternary](https://github.com/quat1024) Jul 2022 for the release of [Hopper](https://github.com/quat1024/hoppers).
* `disaster-time` rewrite branch started by quaternary Dec 2022 - Jun 2023.

This branch is my playground and my domain. Here be dragons (it's me. I'm the dragon)

### Notice

Voldeloom contains a forked copy of some code from [FabricMC/stitch](https://github.com/fabricmc/stitch), moved into the `net.fabricmc.loom.yoinked.stitch` package. Stitch is licensed under the Apache License 2.0, so its license has been reproduced in `src/main/resources/STITCH_REDISTRIBUTION_NOTICE.md`.

# Version status

Using the latest version of Minecraft Forge for each Minecraft version.

* "Compile" -> CI tests that a small sample mod is able to compile. The sample mod links against a few classes from Forge and Minecraft, so at least some amount of project setup works.
* "Release" -> You *might* be able to drop these mods into a production Forge environment.
* "Deobf" -> A working `runClient` environment, with MCP names and IDE debugger, that you can use to test your mod without needing to build a release jar and copy it into a real Minecraft launcher.
	* Yes it's possible to develop mods like that. It's not very fun, but it's possible.
* 🤔 -> I think it's possible? Haven't tested it yet.

|  | Compile? | Release? | Deobf? | |
| --: | :-: | :-: | :-: | :-- |
| <=1.0 | ❌ | ❌ | ❌ | Predates `files.minecraftforge.net`. |
| 1.1 | ❌ | ❌ | ❌ | I don't think there is much interest in modding for this version. |
| 1.2.5 | ❌ | ❌ | ❌ | Mods don't load in dev.<br>Remapping for release is dummied out. |
| 1.3.2 | ✅ | 🤔 | ✅ | |
| 1.4.7 | ✅ | ✅ | ✅ | **Has received the most testing.**<br>Used for several production mods. |
| 1.5.2 | ✅ | ✅ | ✅ | |
| 1.6.4 | ✅ | 🤔 | ✅ | |
| 1.7.10 | ✅ | 🤔 | ✅ | |
| >=1.8 | ❌ | ❌ | ❌ | Out of scope. |

# Caveat creare:

**Take this project with a grain of salt, *especially*** the `runClient` dev workspace.

This project implements a Forge modding toolchain from first principles, using a *radically* different approach than MCP/ForgeGradle ever did. They patch source code, we install Forge like a jarmod. They remap sources, we remap binaries. The MCP parsers and remappers and jar mergers and jar processors and access-transformers and other components used in this plugin share no lineage with anything Forge or MCP ever used. *There are behavioral differences with just about all of these components.* Additionally: there's a healthy dose of secret strips of duct-tape used to get things working, some important aspects of modding that you can't do yet, and a lot that's just plain WIP.

I *strongly* suggest testing the release version of your mod often in a "real" Forge production environment, like a [Prism Launcher](https://prismlauncher.org/) Forge instance. These workspaces are much more well-tested.

Also: this should go without saying, but do not bother the official Forge community with support requests.

# Usage

Start with this `build.gradle`. I would *strongly* suggest using modern tech - Java 17 and Gradle 7.6, to be specific. (Gradle 8 might work too, since `2.2-SNAPSHOT`.)

```groovy
buildscript {
	repositories {
		mavenCentral()
		maven { url "https://maven.fabricmc.net" }
		maven { url "https://repo.sleeping.town" }
	}
	dependencies {
		classpath "agency.highlysuspect:voldeloom:2.4" 
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

Now `./gradlew runClient --info --stacktrace` should perform a bunch of magic culminating in a Minecraft Forge 1.4.7 client window, and as an optional next step, `genSources` will churn out a nice sources jar (with javadoc!) for you to attach in your IDE, like how Loom works.

Then just start modding. Don't worry too much about Java versions - the plugin will provision a Java 8 toolchain for launching the game.

## for 1.7.10

Replace the `mappings` line with:

```groovy
mappings volde.layered {
	importMCPBot("https://mcpbot.unascribed.com/", minecraftVersion, "stable", "12-1.7.10")
}
```

## Using an older version of Java or Gradle

Voldeloom should work all the way down to Java 8 and Gradle 4. If using the sample buildscript above, replace the `java.toolchain`/`compileJava` lines with:

```groovy
compileJava {
	sourceCompatibility = "1.6"
	targetCompatibility = "1.6"
}
```

and since old versions of Gradle are not Java 9-clean and lack the "toolchains" feature, remember to invoke Gradle using a Java 8 JDK.

# Documentation / help

Well I've gotta finish it first! Even this `README` gets outdated worryingly quickly.

* Check the `doc` folder.
* Check the `sample` folder for some sample projects. Some are compiled in CI; it should at least get that far.
  * note the caveat about the weird gradle setup though, you can't just copy all files in the sample project and start working
* See the `LoomGradleExtension` class for a full list of things you can configure from `volde { }`.
* I'm trying to write lots of javadoc?
* [Ask me](https://highlysuspect.agency/discord).

If stuff isn't working:

* Don't use the IDE integration like `genIdeaRuns` yet. It's broken. Just use the `runClient` Gradle task for launching purposes.
* Run with `--info --stacktrace` for much more detailed log output
* Try dumping the cache: run with `--refresh-dependencies` (to enable the global refresh-dependencies mode) or `-Pvoldeloom.refreshDependencies` (to refresh only Voldeloom artifacts)
* Investigate some of the file paths written to the log; see if there's any weird things like corrupt or empty files.

If you're looking for general 1.4 Forge development advice, try [here](https://github.com/quat1024/hoppers/blob/trunk/NOTES.md).

# current status

What works:

* Forge's Maven is configured for you with free bonus `metadataSources` forward-compat magic for gradle 5+
* Downloading Minecraft, merging the jars, remapping, etc (the usual from a minecraft toolchain)
* Patching Minecraft with Forge's classes:
  * 1.5 and below: jarmod style
  * 1.6/maybe 1.7: parse and apply `binpatches.pack.lzma` gdiff archive
* `genSources` (sans methods with MCP messed-up switchmaps), attaching sources in intellij, browsing and find usages, MCP comments in source code
* Asset downloading, in the old file layout that legacy versions use
* `runClient` gets in-game (on at least 1.4.7 and 1.5.2)
  * `shimForgeLibraries` task predownloads Forge's runtime-downloaded deps and places them in the location Forge expects, because the URLs hardcoded in forge are long dead
  * `shimResources` task will copy assets from your local assets cache into the run directory (because you can't configure `--assetsDir` in this version of the game)
* Recognized mapping sources: a Forge `-src` zip, an MCP download, tinyv2 archives (although tinyv2 will break binpatches)
* Mostly-complete backport of the "extendable run configs" thing from newer Fabric Loom versions
  * Define your own run configs, with per-config vm args and system properties and stuff
  * 1.4 doesn't parse any program arguments apart from the username (arg 0) and session token (arg 1)
* `modImplementation`/etc works
  * `coremodImplementation`/etc exists for coremods that exist at runtime, which need special handling (`remappedConfigEntryFolderCopy` task handles it) 
* Gradle 4 and 7 work 
  * On Gradle 7-, use `modCompile` instead of `modImplementation`, and drop the `only` from `modRuntimeOnly` (`implementation`/`runtimeOnly` are a gradle 7 convention)
* On Gradle 6+, a Temurin 8 toolchain is provisioned for run configs. You can configure the version and vendor
  * Done without breaking Gradle 4 source compatibilty in the plugin btw... so its kinda jank
* Access transformers (barely)
  * It's a bit buggy, you will need to `-Pvoldeloom.refreshDependencies` to get them to propagate through.
  * ATs will not be discovered from other mods (by design)
  * The plugin will not help you declare your ATs for the non-development workspace (yet)

What doesn't work:

* Generated IDE run configs are broken.
  * The `runClient` task works, and is more of a priority because I get *much* more control over the startup process
  * (I don't think many modders know what the actual difference between `runClient` and ide runs are, maybe i should write something up)
  * Something is putting a million java 8 jars on the runtime classpath and exploding 1.4.7
* I don't know how broken Eclipse/VSCode are
* Dependency source-remapping and `migrateMappings` are (temporarily?) removed. If you're using retro mapping projects other than MCP....... have Fun
* You can depend on other people's coremods, but you can't develop them (they don't end up in the `coremods` folder where Forge wants to find them)
* You might need to worry about [this sort of thing](https://github.com/unascribed/BuildCraft/commit/06dc8a89f4ea503eb7dc696395187344658cf9c1) - I recently redid the mappings system idk if it's still an issue

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

Breakpoints don't work if you just hit the "refresh gradle" button, but if you select the task in the `Select Run/Debug Configuration` bar, you can press the debug button.

# Common problems for consumers

General debugging stuff:

* You must fill one dependency for *each* of the `minecraft`, `forge` and `mappings` configurations, things will explode otherwise.
* When in doubt, poke around in your Gradle cache (`~/.gradle/caches/fabric-loom`). If there are any obviously messed-up files like zero-byte files, corrupt/incomplete jars or zips, delete them and try again.
  * many of the "minecraft setup" processes are not actual Gradle tasks, so they don't benefit from gradle's correct computations or task-uptodateness
* Run your Gradle task with `--info --stacktrace`. The plugin does spam `--info` with quite a bit of useful stuff.

I agree! There *should* be better error messages!

## Forge whines about getting `e04c5335922c5e457f0a7cd62c93c4a7f699f829` for a couple of dependency hashes

The `shimForgeLibraries` task is intended to download the libraries Forge wants and place them in the locations it expects to find them *before* launching the game, since they were removed from the hardcoded URLs in Forge a long time ago (I think that's the sha1 of the Forge server's 404 page).

Either that task didn't run and the libraries aren't there (examine the Gradle log to see if it ran), or the `minecraft.applet.TargetDirectory` system property did not get set on the client and it's trying to read libraries out of your real `.minecraft` folder - if it's doing that, the rest of the game will also try to run out of that folder (check the path where Forge told you it saved the crash log)

#### Note to players who got here by searching e04c5335922c5e457f0a7cd62c93c4a7f699f829 on google

I would recommend using a launcher that shims this process for you (like [Prism Launcher](https://prismlauncher.org)) if you can, so you don't have to deal with this. If you can't, you will need to shim the libraries manually. To do this, check your `.minecraft` folder for a logfile Forge produced, probably with a name like `ForgeModLoader-client-0.log`. Open it, scroll to the bottom, and look for lines like:

```
There were errors during initial FML setup. Some files failed to download or were otherwise corrupted. You will need to manually obtain the following files from these download links and ensure your lib directory is clean.
*** Download http://files.minecraftforge.net/fmllibs/deobfuscation_data_1.5.2.zip
```

You can obtain the file from Prism Launcher's mirror by replacing `http://files.minecraftforge.net/fmllibs/` with `https://files.prismlauncher.org/fmllibs/`, then putting the URL into your web browser. You can also try putting the URL into the Internet Archive Wayback Machine.

Once you have the file, place it in `.minecraft/lib`, using the filename at the end of the URL (in this example, make sure the file is named `deobfuscation_data_1.5.2.zip`). Repeat for all URLs mentioned in the log file. The next time you start Forge, it should find these files, assume it already downloaded them, and won't make any attempts to contact the dead server.

## Forge NPEing about something in `FMLRelaunchLog`

Forge assumes the `.minecraft` directory exists without checking or creating it. If it doesn't exist an exception will be thrown when it creates its log file, but it silently swallows the exception, so you get an NPE shortly after when it tries to use the log. Because the plugin will try to create the `run` directory if it doesn't exist, this is likely another "the game is not using the correct working directory" bug, so check that the `minecraft.applet.TargetDirectory` system property is set.

## Buuuunch of logspam about "Unable to read a class file correctly" or "probably a corrupt zip"

Something compiled to Java 8's classfile format is on the classpath. Forge 1.4.7 only works with classes compiled for Java 6. (Not sure why this happens when using generated run configs, instead of the gradle runClient task, probably a classpath difference)

## `genSources` NPEs on `ClassWrapper.getMethodWrapper` in methods like `placeDoor`, `getOptionOrdinalValue`, `multiplyBy32AndRound` etc

When desugaring a switch-over-enum, this version of Fernflower assumes its associated "switchmap" class is named with the same convention that `javac` uses when compiling switch-over-enum, and will crash if it can't find it. Mojang proguarded the switchmap classes and MCP went back and renamed them, but gave them the "wrong" name, causing the bug. See [this page on the CFR website](https://www.benf.org/other/cfr/switch-on-enum.html) for more information about switch-over-enum, and `quat_notes/weird_enum_switch_methods.md` for some of my notes.

This is a binary-based toolchain where the decompiler output is just for show, so a method failing to decompile is not a big deal. If you need to see the body of the method you can try Quiltflower, the built-in IntelliJ decompiler, or CFR.

# Notes page

TODO: formalize these and put them into `doc`

See `quat_notes/old notes.md` for stuff that used to be on this page but got outdated.

## Architecture (new updated early ~~jan~~ ~~feb~~ apr 2023) (working) (free download)(no virus)

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
    * `minecraftDependencies`
       * Minecraft's own libraries, like LWJGL
    * `forge`
       * Forge artifact straight off of Maven
    * `forgeClient`, `forgeServer`
       * Split Forge artifacts straight off of Maven (for <=1.2.5, which was two separate artifacts)
    * `forgeDependencies`
       * Forge's autodownloaded libraries, like Guava
    * `mappings`
       * MCP mappings artifact. This is either something straight off of Maven (if you're sourcing mappings from a Forge `src` zip) or a file on your computer (if using `volde.layered` mappings)
    * `accessTransformers`
       * Custom Forge-format access transformer files.  
    * `minecraftNamed` - extends `minecraftDependencies`, `forgeDependencies`
        * Minecraft named with your chosen mappings
        * `compile`/`implementation` extends from this (so you can code against it)
    * and a couple for mod dependencies. These are added in pairs, and stowed in a "remapped configuration entries" list that later tasks will use
    * `modImplementation` and `modImplementationNamed`
        * `implementation` extends `modImplementationNamed`
        * ends in `compile` instead of `implementation` on Gradle 6-
    * `modCompileOnly` and `modCompileOnlyNamed`
        * `compileOnly` extends `modCompileOnlyNamed`
    * `modRuntimeOnly` and `modRuntimeOnlyNamed`
        * `runtimeOnly` extends `modRuntimeOnlyNamed`
        * no `Only` suffix on Gradle 6-
    * `modLocalRuntime` and `modLocalRuntimeNamed`
        * `runtimeOnly` extends `modLocalRuntimeNamed`
    * Some for coremods, which require special handling: 
    * `coremodImplementation` and `coremodImplementationNamed`
        * `compileOnly` extends `coremodImplementationNamed`
    * `coremodRuntimeOnly` and `coremodRuntimeOnlyNamed`
        * no extensions
    * `coremodLocalRuntime` and `coremodLocalRuntimeNamed`
        * no extensions
7. some IntelliJ IDEA settings are configured, same stuff you could do if you wrote an `idea { }` block in the script
8. All the Gradle tasks are registered
    * remapJar
    * genSources
    * genEclipseRuns, genIdeaRuns, genIdeaWorkspace, vscode
    * (my fork) shimForgeLibraries, shimResources, remappedConfigEntryFolderCopy
    * the debugging task printConfigurationsPlease
    * It's set up so that adding a run config to `volde.runs` will add a `runXxx` task for it, then the `client` and `server` run configs are created which adds the runClient and runServer tasks

Then we ask for an `afterEvaluate` callback. The rest of the buildscript in your project runs first, so when the callback runs, it is able to access the settings configured in the `volde { }` block:

1. Call any "before minecraft setup actions" (callback that the buildscript author can use for any purpose)
2. Call `ProviderGraph#trySetup()`. This is a deeply magical Does-It-All method. Described later.
3. The `jar` and `remapJarForRelease` tasks are wired up:
   * If `remapJarForRelease` doesn't have an input:
      * `jar` is set to a classifier of `"dev"` and `remapJarForRelease` is set to a classifier of `""`.
      * `remapJarForRelease`'s input is set to `jar`'s output.
   * `remapJar`'s output is registered to the `archives` artifact configuration.
   * The Loom gradle extension gets `addUnmappedMod` called on it, set to `jar`'s output (idk)
4. Maven publication settings are configured, to forward mod dependencies into your maven POM.
5. Call any "after minecraft setup actions" (callback that the buildscript author can use for any purpose)

After doing all of that, the task execution phase may begin.

What happens in `ProviderGraph#trySetup`:

1. Ensure there's exactly one dependency in the `minecraft` configuration. Reads its version number.
2. `VanillaJarFetcher`:
   * Download `version_manifest.json`, locate the appropriate per-version manifest.
   * Download the client and server.
3. `VanillaDependencyFetcher`:
   * Examine the per-version manifest for Maven-style dependencies and native libraries.
   * Add the Maven-style dependencies to the Gradle project as regular maven deps.
   * Download the native libraries to a folder.
4. `AssetDownloader`:
   * Just configure it, don't actually download the assets yet.
5. If there's something in the `forge` configuration:
   * `Binpatcher`:
     * Examine the jar for 1.6+-style "binpatches".
     * If they exist, apply binpatches to the client and server jars.
   * `Merger`:
     * Blend the client and server jars into a single `-merged` jar.
   * (Continue from step 7, on the merged jar & merged copy of Forge.)
6. If there's instead something in the `forgeClient` and `forgeServer` jars:
   * (Continue from step 7, on the client jar & client version of Forge.)
   * (Continue from step 7, on the server jar & server version of Forge.)
7. `ForgeDependencyFetcher`:
   * Examine the version of Forge for its additional dependencies.
   * There's various odd formats used over the years, like "automatically-downloaded libraries at runtime" or a `version.json` launcher profile. Handle those.
8. `Jarmodder`:
   * Copy the Forge jar into the vanilla Minecraft jar.
   * Delete `META-INF`.
9. `MappingsWrapper`:
   * Ensure there's exactly one dependency in the `mappings` configuration.
   * Read various MCP `.srg`/`.csv` files and construct `McpMappings` from them.
10. (or step 11, on 1.7+) `AccessTransformer`:
    * Apply Forge's access-transformers to the minecraft jar.
11. (or step 10, on 1.7+) `RemapperMcp`:
    * Apply the `McpMappings` to the jar, creating an SRG-named jar (func_, field_, etc)
12. `NaiveRenamer`:
    * Apply the `fields.csv` and `methods.csv` names from the `McpMappings`.
13. `DependencyRemapperMcp`:
    * Takes mods from the `modImplementation`/etc configurations and remaps them from the release namespace into the workspace names.
14. configure `GenSourcesTask.SourceGenerationJob`s.
15. Adding the finished Minecraft jar to the `minecraft` configuration:
    * Use the `-linemapped` jar from the last `genSources` execution, if one exists.
    * If not, the output of `NaiveRenamer` is used.
16. Create reobf mappings, that go from the workspace namespace into the release namespace, used by the `remapJarForRelease` task.

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

Forge 1.4.7 has a limitation where it cannot load coremods from the classpath; they *must* exist in the coremods folder only. If you set `copyToFolder("coremods")` on a remapped dependency entry, a Gradle task that runs before any `runXxxx` tasks (`RemappedConfigEntryFolderCopyTask`) will notice, and copy the dependency into the coremods folder for you.

The predefined `coremodImplementation`/`coremodImplementationNamed` entry, for example, only sets `coremodImplementationNamed` to extend from `compileOnly`, not `implementation`. This means it doesn't get put on the runtime classpath the usual way (by way of `runtimeClasspath` extending `implementation`). Forge picks up on the mod because the jar has been copied into the `coremods` folder, though.

There are `coremodImplementation`, `coremodRuntimeOnly`, and `coremodLocalRuntime` configurations predefined. `coremodCompileOnly` does *not* exist because the folder-copy workaround is only required to load the coremod *in the local development workspace*; if it existed, it would be identical to `modCompileOnly`, so just use that.