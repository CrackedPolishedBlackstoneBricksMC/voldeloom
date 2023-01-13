# Your tour of examples

### 1.4.7

Minecraft 1.4.7 using Gradle 7.6. This is the one I test against most.

You may execute this with any JVM. The `toolchains` feature selects a compiling JVM that is able to write classes with version 50 (Java 6). Any newer class file versions will crash Forge when it scans the jar.

### 1.4.7-gradle4

Minecraft 1.4.7 using Gradle 4.10.3.

This must be executed on a Java 8 JVM, because:

* gradle 4 is not compatible with newer jvms
* the `toolchains` feature doesn't exist yet.

### `dep_testin`

Not actually a sample project, this contains some definitely-legal-to-redistribute mod binaries for the other projects to depend on. 