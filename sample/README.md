# Your tour of examples

In IntelliJ, right click on a sample project's build.gradle and press "Link Gradle project" to add it to the Gradle tool window. Right click on projects in the window and press "Unlink Gradle project" to forget them.

Also, note the little hack in each project's `settings.gradle`; this just makes them work from a subfolder.

Github Actions currently builds `1.3.2`, `1.4.7`, `1.4.7-gradle4`, and `1.5.2` on Java 8, and Java 17 except for the `-gradle4` one. It merely tests that the projects compile, doesn't attempt to run them (a little `runServer` wouldn't be a bad idea)

### `dep_testin`

Not actually a sample project; contains some definitely-legal-to-redistribute mod binaries for the other projects to depend on.

### 1.2.5

Forge 1.2.5-3.4.9.171 using Gradle 7.6.

**Broken.**

You get vanilla, and (somehow) two Forges, but the mod under development doesn't load.

Remapping the jar for release doesn't work, it's dummied out while I figure out a better system more conducive to split jars.

Project will require some big edits to *properly* (i.e. not slapped-together) support the split jars, ideally with an environment that's "nice" (client/server source sets like Loom 1?)

### 1.3.2

Forge 1.3.2-4.3.5.318 using Gradle 7.6.

**Works?** I finished this at about 4 in the morning and didn't test it for too long. But I think it works.

Tiny cheat to get Bouncycastle onto the classpath (I pretend it's a Forge library, mirroring [this commit](https://github.com/MinecraftForge/FML/commit/a513060a81ac4b245b4f19b5ac3e589eb15e3515) from 1.4 FML)

Forge prints a *million* warnings about someone putting their mod in the `net.minecraft.src` package, even though it's actually just picking up on Minecraft being in that package. I don't know what package Forge *wants* minecraft to be in, given that that's the package MCP *puts* it in. They can be ignored.

### 1.4.7

Forge 1.4.7-6.6.2.534 using Gradle 7.6.

**Works great.** This is the first version to receive attention, and the one I test against the most.

You may execute this project with any JVM. The `toolchains` feature selects a compiling JVM that is able to write classes with version 50 (Java 6). Any newer class file versions will crash Forge when it scans the jar.

### 1.4.7-gradle4

Forge 1.4.7-6.6.2.534 using Gradle **4.10.3**.

**Works great.** This project must be executed on a Java 8 JVM, because gradle 4 is not compatible with newer jvms, and the `toolchains` feature doesn't exist yet to select the correct compiler.

### 1.4.7-gradle8

Forge 1.4.7-6.6.2.534 using Gradle **8.0**.

**Works great.** This is another "curiosity" project. I don't know if I recommend using Gradle 8 just yet (a little too cutting-edge for my tastes)

### 1.4.7-thingy

Forge 1.4.7-6.6.2.534 using Gradle 7.6 and [unascribed's UnknownThingy mapping set](https://git.sleeping.town/unascribed/UnknownThingy) instead of MCP.

Currently **broken**, likely due to the mappings not playing well with tiny-remapper and Voldeloom not being equipped to handle intermediaryless mappings. At least it is broken in a way that indicates it's trying to load the mappings!!!

### 1.5.2

Forge 1.5.2-7.8.1.738 using Gradle 7.6.

**Works great** ever since i fixed the access transformer logic.

### 1.6.4

Forge 1.6.4-9.11.1.1345 using Gradle 7.6.

**Works great** ever since I added binpatches.pack.lzma support and Launchwrapper support.

### 1.7.10

Forge 1.7.10-10.13.4.1614-1.7.10 (yes it says 1.7.10 twice) using Gradle 7.6.

**Works.** Mappings are very incomplete and full of SRGs (I was informed the currently-used ones from the Forge 1.7.10 sources repo are outdated, but don't have a mechanism to work with the newer-format mappings yet. Blocked on the hypothetical Good Mappings System...)