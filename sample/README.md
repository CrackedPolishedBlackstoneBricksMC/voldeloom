# Your tour of examples

In IntelliJ, right click on a sample project's build.gradle and press "Link Gradle project" to add it to the Gradle tool window. Right click on projects in the window and press "Unlink Gradle project" to forget them.

Also, note the little hack in each project's `settings.gradle`; this just makes them work from a subfolder.

Github Actions currently builds `1.3.2`, `1.4.7`, `1.4.7-gradle4`, and `1.5.2` on Java 8, and Java 17 except for the `-gradle4` one. It merely tests that the projects compile, doesn't attempt to run them (a little `runServer` wouldn't be a bad idea)

### `dep_testin`

Not actually a sample project; contains some definitely-legal-to-redistribute mod binaries for the other projects to depend on.

### 1.2.5

Forge 1.2.5-3.4.9.171 using Gradle 7.6.

**Completely broken.** Buildscript hangs on `MergedProvider`.

It will require significant effort to properly deal with split "client" and "server" jars that are two real, separate codebases.

### 1.3.2

Forge 1.3.2-4.3.5.318 using Gradle 7.6.

**Works?** I finished this at about 4 in the morning and didn't test it for too long. But I think it works.

Tiny cheat to get Bouncycastle onto the classpath (I pretend it's a Forge library, mirroring [this commit](https://github.com/MinecraftForge/FML/commit/a513060a81ac4b245b4f19b5ac3e589eb15e3515) from 1.4 FML)

Forge prints a *million* warnings about someone putting their mod in the `net.minecraft.src` package, even though it's actually just picking up on Minecraft being in that package. I don't know what package Forge *wants* minecraft to be in, given that that's the package MCP *puts* it in.

### 1.4.7

Forge 1.4.7-6.6.2.534 using Gradle 7.6.

**Works great.** This is the first version to receive attention, and the one I test against the most.

You may execute this project with any JVM. The `toolchains` feature selects a compiling JVM that is able to write classes with version 50 (Java 6). Any newer class file versions will crash Forge when it scans the jar.

### 1.4.7-gradle4

Forge 1.4.7-6.6.2.534 using Gradle **4.10.3**.

**Works great.** This project must be executed on a Java 8 JVM, because gradle 4 is not compatible with newer jvms, and the `toolchains` feature doesn't exist yet to select the correct compiler.

### 1.4.7-gradle8

Forge 1.4.7-6.6.2.534 using Gradle **8.0**.

This is another "curiosity" project. I don't know if I recommend using Gradle 8 just yet (a little too cutting-edge for my tastes)

### 1.4.7-thingy

Forge 1.4.7-6.6.2.534 using Gradle 7.6 and [unascribed's UnknownThingy mapping set](https://git.sleeping.town/unascribed/UnknownThingy) instead of MCP.

Currently **broken**, likely due to the mappings not playing well with tiny-remapper and Voldeloom not being equipped to handle intermediaryless mappings. At least it is broken in a way that indicates it's trying to load the mappings!!!

### 1.5.2

Forge 1.5.2-7.8.1.738 using Gradle 7.6.

**Works great** ever since i fixed the access transformer logic.

### 1.6.4

Forge 1.6.4-9.11.1.1345 using Gradle 7.6.

**Works, quietly.**

Used to be completely busted due to missing `binpatches.pack.lzma` support + missing Launchwrapper support.

Now the main problem is that it doesn't download the sounds in the right location, so the game launches but there's no sound.

1. tiny-remapper fails due to mapping name conflicts. It's not wrong, some classes like `bga` (`RenderBat`) end up with two mappings from the same method name to different SRG names, and this is honest to what the SRGs say.
   * "fixed" due to hacking the name conflicts out of the way in `Srg.java`, just so i could at least get the gradle project to import.
2. `net.minecraft.client.Minecraft` doesn't have a `main` method anymore.
   * Fixed by getting a lil basic support for launchwrapper
3. Invalid `ClientBrandRetriever` jar signature, which trips `FMLSanityChecker`.
   * Impossible to fix because pre-binpatching all the Forge stuff is going to break the signature anyway,
   * "Fixed" by passing `-Dfml.ignoreInvalidMinecraftCertificates=true`, which makes this class chill out a bit, lol
4. No sound.
   * This is when Mojang switched to the new file layout (with file hashes).
   * Also the game supports --assetIndex natively now :bangbang:

### 1.7.10

Forge 1.7.10-10.13.4.1614-1.7.10 (yes it says 1.7.10 twice) using Gradle 7.6.

~~**FUBARd.**, mainly because the `-sources` artifact on their Maven is fake and it's actually the mod development kit, lol~~ Using a shell script to `git clone` Forge: **Everything is busted**, SRGs are there but MCP names are missing, access transformers seem to go unused, game crashes due to `aji$4` illegal access error, a million other things. At the very least binpatches are working