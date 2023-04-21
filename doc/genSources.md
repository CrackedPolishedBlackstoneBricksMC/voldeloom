Voldeloom is a *binary-based toolchain*, which means it always works with compiled Java `.class` files and never attempts to parse or manipulate Java sources. Most IDEs even include a built-in decompiler for browsing the Minecraft source code. Unlike with ForgeGradle-style source-based toolchains, running a decompiler is *optional* with Voldeloom.

Nevertheless, there's a few reasons you might want to run the `genSources` task anyway:

* Voldeloom can insert comments sourced from MCP mappings into the decompiled code.
* Sometimes IntelliJ is picky and works better when you have an attached sources jar ("find usages" is very flaky without one)
* Line number information.

# Linemapping

Compiled Java code may contain a line-number table, which corresponds regions of JVM bytecode to lines of Java source code. The line numebr table is used when annotating exception stacktraces, but it's critical to the functioning of a debugger - when you place a breakpoint on "line 59", what actual instruction should the VM stop on, and when you press "next line", how many instructions should the JVM execute? Mojang did not strip out the line-number table from release versions of Minecraft, but the line numbers inside it correspond to lines in Mojang's internal source code, not the decompiled source code we have access to. That's a bummer. There's two possible ways around this:

* Recompile the decompiler output, creating a fresh linemap table.

This *would* be the best solution, but recomp is very hard and requires manual patching to fix decompiler flaws. This is a binary-based toolchain anyway.

* Edit the linemap table in the original jar.

Loom (and Voldeloom) takes this approach. Luckily, Fabric's fork of Fernflower can write a "linemap" file, mapping line-number table information in the compiled original jar to physical lines of Fernflower's output. We can adjust the linemap in the original jar to match Fernflower's output. This is called *linemapping* and it's done automatically when you run `genSources`. If a linemapped jar exists in the Gradle cache, Voldeloom will add it as a project dependency instead of the regular Minecraft jar the next time you use Gradle. Make sure you're also referencing the linemapped jar in whatever tool you're using to place breakpoints (the filename will end in `-linemapped.jar`) - this may require a "refresh" whack.

# A note about methods that fail to decompile

Due to MCP errata and a Fernflower bug, some methods containing a switch-over-enum cause Fernflower to throw an exception. In 1.4.7, these are `multiplyBy32AndRound`, `placeDoor`, `getAppDir`, and `getOptionOrdinalValue`. Fernflower will print errors about this. You may also see errors of the form "Unable to simplify switch on enum".

These are safe to ignore and do not affect the functioning of the game. You can still `runClient` and calls to the methods will still work.

# Configuring

Some options you can configure:

```groovy
tasks.named("genSources").configure {
	it.numThreads = 2
	it.saferBytecodeProvider = false
	it.skipDecompile = false
	it.linemapDebug = false
}
```

`genSources` currently uses Fabric's fork of Fernflower, which can decompile multiple classes at the same time, and *`numThreads`* controls the amount of threads. This defaults to the number of physical CPU cores minus one.

If *`saferBytecodeProvider`* is set, the stock Fernflower file-reading code is used, and if it's unset (the default) it'll use a trivial-but-kinda-fast optimization I wrote. Part of the reason it's faster is that it doesn't validate as much, so, experiencing problems, turn it off.

*`skipDecompile`* will skip the actual source-gen part of genSources and only redo the linemapping.

*`linemapDebug`* will write an alternate version of the `-sources` jar ending in `-linemap-debug.jar`, that annotates each line of decompiled code with what line of *Mojang's* source code it corresponds to. You can attach this in your editor instead of the regular `-sources` jar to shed some light on weird debugger behaviors - sometimes a line of code doesn't have any line-number table information because it's something Fernflower invented from thin air, and sometimes Fernflower decided to sugar the source code differently from Mojang.

Here, Fernflower formatted the `if` body on a separate line, but Mojang must have put it on the same line:

```java
            public SaveFormatOld(File var1) {
/* 18 */       if (!var1.exists()) {
                  var1.mkdirs();
               }
         
/* 19 */       this.savesDirectory = var1;
/* 20 */    }
```

Here, the debugger has trouble single-stepping from the first line of the constructor to the second line, which the big line-number discontinuity somewhat explains:

```java
             public EntityRenderer(Minecraft par1Minecraft) {
/*  48 */       this.mc = par1Minecraft;
/* 193 */       this.itemRenderer = new ItemRenderer(par1Minecraft);
/* 194 */       this.lightmapTexture = par1Minecraft.renderEngine.allocateAndSetupTexture(new BufferedImage(16, 16, 1));
/* 195 */       this.lightmapColors = new int[256];
/* 196 */    }
```