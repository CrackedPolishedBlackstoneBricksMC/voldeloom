# Voldeloom-disastertime

Gradle plugin for ~~Fabric~~ ancient versions of Forge.

* **Latest stable**: `2.4`
* **Latest snapshot**: `2.5-SNAPSHOT` (from CI every commit)

## History

* Based off prerelease version of Fabric Loom 0.4 by [Loom contributors](https://github.com/TwilightFlower/fabric-loom/graphs/contributors) Aug 2016 - Jun 2020.
* [Forked](https://github.com/TwilightFlower/fabric-loom/) by [TwilightFlower](https://github.com/TwilightFlower/) May 2020 for the release of [Retro Tater](https://github.com/TwilightFlower/retro-tater); she did lots of the initial architecture work.
* [Tweaked, maintained, and additional version support](https://github.com/unascribed/voldeloom/) by [unascribed](https://github.com/unascribed/) May 2021 - May 2022 for the release of [Ears](https://git.sleeping.town/unascribed/Ears/src/branch/trunk/platform-forge-1.4) and other Forge mods.
* Minor tweak by [quaternary](https://github.com/quat1024) Jul 2022 for the release of [Hopper](https://github.com/quat1024/hoppers).
* `disaster-time` rewrite branch started by quaternary Dec 2022 - Jun 2023, some in 2025

This branch is my playground and my domain. Here be dragons (it's me. I'm the dragon)

### Project status

*Somewhat* maintained. I've ran into some problems.

* It's not flexible enough. You're still bound to exactly one minecraft per gradle project, meaning 1.2 is still broken. Hacky special-cases are peppered throughout the code, like all of `ForgeCapabilities`.
* I eschew Gradle's task model (for good reason?) but the "provider" system replacing it is poorly thought out.
* I wish parts of the code were more easily separable from the Gradle project as a whole. I'm proud of the MCP parsing and binpatching code, for example, seems a waste to hide it in here.

So I am slowly chipping away at [toybox](https://codeberg.org/CrackedPolishedBlackstoneBricksMC/toybox). I'm also focusing on the Mill build system since it works much better with this project's needs than Gradle (and also just to try it).

### Provenance

Voldeloom contains a forked copy of some code from [FabricMC/stitch](https://github.com/fabricmc/stitch), moved into the `net.fabricmc.loom.yoinked.stitch` package. Stitch is licensed under the Apache License 2.0, so its license has been reproduced in `src/main/resources/STITCH_REDISTRIBUTION_NOTICE.md`.

Voldeloom contains code adapted from [the public-domain LZMA Java SDK](https://7-zip.org/sdk.html).

# Version status

Using the latest version of Minecraft Forge for each Minecraft version.

* "Compile" -> CI tests that a small sample mod is able to compile. The sample mod links against a few classes from Forge and Minecraft.
* "Release" -> You can drop these mods into a production Forge environment and they will work. Probably.
* "Deobf" -> A working `runClient` environment complete with MCP names and IDE debugger.
* 🤔 -> Maybe?

|        | Compile? | Release? | Deobf? |                                                                         |
|-------:|:--------:|:--------:|:------:|:------------------------------------------------------------------------|
|  <=1.0 |    ❌     |    ❌     |   ❌    | Predates `files.minecraftforge.net`.                                    |
|    1.1 |    ❌     |    ❌     |   ❌    | I don't think there is much interest in modding for this version.       |
|  1.2.5 |    ❌     |    ❌     |   ❌    | Mods don't load in dev.<br>Remapping for release is dummied out.        |
|  1.3.2 |    ✅     |    🤔    |   ✅    |                                                                         |
|  1.4.7 |    ✅     |    ✅     |   ✅    | **Has received the most testing.**<br>Used for several production mods. |
|  1.5.2 |    ✅     |    ✅     |   ✅    |                                                                         |
|  1.6.4 |    ✅     |    🤔    |   ✅    |                                                                         |
| 1.7.10 |    ✅     |    🤔    |   🤔   | Mod wasn't loading in dev, todo?                                        |
|  >=1.8 |    ❌     |    ❌     |   ❌    | Out of scope.                                                           |

# Caveat creare:

**Take this project with a grain of salt, *especially*** the `runClient` dev workspace.

This project implements a Forge modding toolchain from first principles using a *radically* different approach than MCP/ForgeGradle ever did. They patch source code, we install Forge like a jarmod. They remap sources, we remap binaries. The MCP parsers and remappers and jar mergers and jar processors and access-transformers and other components used in this plugin share no lineage with anything Forge or MCP ever used. *There are behavioral differences with just about all of these components.*

Even if there is a green tick in the above chart, I *strongly* suggest testing the release version of your mod often in a "real" Forge production environment, like a [Prism Launcher](https://prismlauncher.org/) Forge instance. These workspaces are much more well-tested.

# Usage

Start with this `build.gradle`. Modern tech, such as Java 17/21 and Gradle 7/8, should work just as well as ancient tech, such as Gradle 4 and Java 8.

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

Now `./gradlew runClient --info --stacktrace` should pop a Minecraft Forge 1.4.7 client window, and as an *optional* next step, `genSources` will churn out a nice sources jar for you to attach in your IDE. Then just start modding. Voldeloom will provision a Java 8 JDK for launching the game.

## for 1.7.10

Replace the `mappings` line with:

```groovy
mappings volde.layered {
	importMCPBot("https://mcpbot.unascribed.com/", minecraftVersion, "stable", "12-1.7.10")
}
```

## for Gradle 4.x / Java 8

Gradle 4 predates the "toolchains" feature, so you will need to invoke Gradle with a Java 8 JDK to successfully build projects and run `runClient`. In the sample buildscript above, replace the `java.toolchain`/`compileJava` lines with this:

```groovy
compileJava {
	sourceCompatibility = "1.6"
	targetCompatibility = "1.6"
}
```

# Documentation / help

This is an unfinished and unpaid hobby project, so expect documentation on-par with that.

* Check the `doc` folder.
* Check the `sample` folder for some sample projects. Some are compiled in CI; it should at least get that far.
  * note the caveat about the weird gradle setup though, you can't just copy all files in the sample project and start working
* See the `LoomGradleExtension` class for a full list of things you can configure from `volde { }`.
* I'm trying to write lots of javadoc?
* [Ask me](https://highlysuspect.agency/discord).

If stuff isn't working, see `doc/troubleshooting.md`

If you're looking for general 1.4 Forge development advice, try [here](https://github.com/quat1024/hoppers/blob/trunk/NOTES.md).

# Wasn't this README longer?

Yeah, but it was getting out of hand so I moved everything else to [./doc](./doc/README.md).
