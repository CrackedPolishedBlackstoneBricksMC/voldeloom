# Your tour of examples

In IntelliJ, right click on a sample project's build.gradle and press "Link Gradle project" to add it to the Gradle tool window. Right click on projects in the window and press "Unlink Gradle project" to forget them.

Also, note the little hack in each project's `settings.gradle`; this just makes them work from a subfolder.

Github Actions currently builds `1.3.2`, `1.4.7`, `1.4.7-gradle4`, and `1.5.2` on Java 8 and Java 17 (except for the `-gradle4` one). It merely tests that the projects compile, doesn't attempt to run them. (a little `runServer` wouldn't be a bad idea)

### `dep_testin`

Not actually a sample project; contains some definitely-legal-to-redistribute mod binaries for the other projects to depend on.

### 1.2.5

Forge 1.2.5-3.4.9.171 using Gradle 7.6.

**Completely broken.** Buildscript hangs on `MergedProvider`.

It will require significant effort to properly deal with split "client" and "server" jars that are two real, separate codebases.

### 1.3.2

Forge 1.3.2-4.3.5.318 using Gradle 7.6.

**Broken.**

Remaps and gets to the menu, but Forge prints a *million* warnings about someone putting their mod in the `net.minecraft.src` package, even though it's actually just picking up on Minecraft being in that package. I don't know what package Forge *wants* minecraft to be in, given that that's the package MCP *puts* it in.

Creating a world crashes due to an NPE in `ClassReader.<init>` called from `SideTransformer.transform` which causes Bouncycastle's CipherOutputStream class to not load. I also needed a kludge in `McpTinyv2Writer` that provides exceptions to the "use srgs when there's no mcp name" rule, something about `JdomParser.parse`. Not lost on me that these are both external libraries.

### 1.4.7

Forge 1.4.7-6.6.2.534 using Gradle 7.6.

**Works great.** This is the first version to receive attention, and the one I test against the most.

You may execute this with any JVM. The `toolchains` feature selects a compiling JVM that is able to write classes with version 50 (Java 6). Any newer class file versions will crash Forge when it scans the jar.

### 1.4.7-gradle4

Forge 1.4.7-6.6.2.534 using Gradle **4.10.3**.

**Works great.** This must be executed on a Java 8 JVM, because gradle 4 is not compatible with newer jvms, and the `toolchains` feature doesn't exist yet to select the correct compiler.

### 1.5.2

Forge 1.5.2-7.8.1.738 using Gradle 7.6.

**Works great** ever since i fixed the access transformer logic.

### 1.6.4

Forge 1.6.4-9.11.1.1345 using Gradle 7.6.

**Big broken**

1. tiny-remapper fails due to mapping name conflicts. It's not wrong, some classes like `bga` (`RenderBat`) end up with two mappings from the same method name to different SRG names, and this is honest to what the SRGs say.
   * "fixed" due to hacking the name conflicts out of the way in `Srg.java`, just so i could at least get the gradle project to import.
2. Forge is not a jarmod in this version (it uses `binpatches.pack.lzma`), so will need to redo patching logic.
   * You get vanilla, because none of the patches do anything.
   * **Ok, I looked into it and the main trouble is the pack200 format that this file is compressed with, which is a complete fucking mess**
   * Later.
3. `net.minecraft.client.Minecraft` doesn't have a `main` method anymore.
   * Fixable by setting the `mainClass` to `net.minecraft.client.main.Main`, where it moved to.
   * FML relauncher is gone. Minecraft is no longer designed to be an applet.
   * This is when the "new launcher" came around (see https://github.com/MinecraftForge/FML/wiki/FML-and-the-new-launcher-in-1.6 ) btw.
4. No sound.
   * This is when Mojang switched to the new file layout (with file hashes).
   * Also the game supports --assetIndex natively :bangbang:
   