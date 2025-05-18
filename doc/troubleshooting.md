# Troubleshooting

General debugging advice:

* You must fill one dependency for *each* of the `minecraft`, `forge` and `mappings` configurations, things will explode otherwise.
* Run your Gradle task with `--info --stacktrace`. Voldeloom is very chatty, but is especially chatty over `--info`.
* When in doubt, poke around in your Gradle cache (`~/.gradle/caches/voldeloom`). If there are any obviously messed-up files like zero-byte files, corrupt/incomplete jars or zips, delete them and try again.
    * many of the "minecraft setup" processes are not actual Gradle tasks, so they don't benefit from gradle's correct computations or task-uptodateness

I agree! There *should* be better error messages!

## Forge whines about getting `e04c5335922c5e457f0a7cd62c93c4a7f699f829` for a couple of dependency hashes

The `shimForgeLibraries` task is intended to download the libraries Forge wants and place them in the locations it expects to find them *before* launching the game, since they were removed from the hardcoded URLs in Forge a long time ago (I think that's the sha1 of the Forge server's 404 page).

Either that task didn't run and the libraries aren't there (examine the Gradle log to see if it ran), or the `minecraft.applet.TargetDirectory` system property did not get set on the client and it's trying to read libraries out of your real `.minecraft` folder - if it's doing that, the rest of the game will also try to run out of that folder (check the path where Forge told you it saved the crash log)

<details><summary>Note to players who got here by searching e04c5335922c5e457f0a7cd62c93c4a7f699f829 on google (click to expand)</summary>

I would recommend using a launcher that shims this process for you (like [Prism Launcher](https://prismlauncher.org)) if you can, so you don't have to deal with this. If you can't, you will need to shim the libraries manually.

To do this, check your `.minecraft` folder for a logfile Forge produced, probably with a name like `ForgeModLoader-client-0.log`. Open it, scroll to the bottom, and look for lines like:

```
There were errors during initial FML setup. Some files failed to download or were otherwise corrupted. You will need to manually obtain the following files from these download links and ensure your lib directory is clean.
*** Download http://files.minecraftforge.net/fmllibs/deobfuscation_data_1.5.2.zip
```

You can obtain the file from Prism Launcher's mirror by replacing `http://files.minecraftforge.net/fmllibs/` with `https://files.prismlauncher.org/fmllibs/`, then putting the URL into your web browser. You can also try putting the URL into the Internet Archive Wayback Machine.

Once you have the file, place it in `.minecraft/lib`, keeping the filename at the end of the URL (in this example, make sure the file is named `deobfuscation_data_1.5.2.zip`). Repeat for all URLs mentioned in the log file. The next time you start Forge, it should find these files, assume it already downloaded them, and won't make any attempts to contact the dead server.

</details>

## Forge NPEing about something in `FMLRelaunchLog`

Forge assumes the `.minecraft` directory exists without checking or creating it. If it doesn't exist an exception will be thrown when it creates its log file, but it silently swallows the exception, so you get an NPE shortly after when it tries to use the log. Because the plugin will try to create the `run` directory if it doesn't exist, this is likely another "the game is not using the correct working directory" bug, so check that the `minecraft.applet.TargetDirectory` system property is set.

## Buuuunch of logspam about "Unable to read a class file correctly" or "probably a corrupt zip"

Something compiled to Java 8's classfile format is on the classpath. Forge 1.4.7 only works with classes compiled for Java 6. (Not sure why this happens when using generated run configs, instead of the gradle runClient task, probably a classpath difference)

## `genSources` NPEs on `ClassWrapper.getMethodWrapper` in methods like `placeDoor`, `getOptionOrdinalValue`, `multiplyBy32AndRound` etc

When desugaring a switch-over-enum, this version of Fernflower assumes its associated "switchmap" class is named with the same convention that `javac` uses when compiling switch-over-enum, and will crash if it can't find it. Mojang proguarded the switchmap classes and MCP went back and renamed them, but gave them the "wrong" name, causing the bug. See [this page on the CFR website](https://www.benf.org/other/cfr/switch-on-enum.html) for more information about switch-over-enum, and `quat_notes/weird_enum_switch_methods.md` for some of my notes.

This is a binary-based toolchain where the decompiler output is just for show, so a method failing to decompile is not a big deal. If you need to see the body of the method you can try Quiltflower, the built-in IntelliJ decompiler, or CFR.
