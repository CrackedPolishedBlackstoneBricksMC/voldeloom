# Your tour of examples

In IntelliJ, right click on a sample project's build.gradle and press "Link Gradle project" to add it to the Gradle tool window. Right click on projects in the window and press "Unlink Gradle project" to forget them.

Also, note the little hack in each project's `settings.gradle`; this just makes them work from a subfolder.

### `dep_testin`

Not actually a sample project; contains some definitely-legal-to-redistribute mod binaries for the other projects to depend on.

### 1.2.5

Forge 1.2.5-3.4.9.171 using Gradle 7.6.

**Completely broken.** Buildscript hangs on `MergedProvider`.

It will require significant effort to properly deal with split "client" and "server" jars that are two real, separate codebases.

### 1.3.2

Forge 1.3.2-4.3.5.318 using Gradle 7.6.

**Broken.** Gets ingame, but Forge doesn't pick up on the mod under development. Game is still proguarded, mappings aren't applying, probably a difference in the mcp format.

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

**Completely broken.** tiny-remapper fails due to mapping name conflicts. Probably a difference in the mcp format.