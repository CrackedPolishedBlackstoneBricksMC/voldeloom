Running changelog document, will be added to as I commit things.

# Next version: 2.1 (`agency.highlysuspect:voldeloom:2.1-SNAPSHOT`)

* **(breaking)** Changed the Gradle extension's name from `minecraft { }` to `volde { }`.
  * Preparation for adding some more free-functions to the extension (it'd be weird if you referred to them with `minecraft`)
  * Possibly preparation for "getting out of the way of other extensions", so you can use it and another Minecraft plugin in the same project? Maybe?
  * Echoes what Loom did - the name is more accurate
* The project-wide `offline` and `refreshDependencies` flags were moved from `Constants` into the extension:
  * Set the `voldeloom.offline` system property, project property, pass `--offline` to Gradle, or configure `offline` in the extension to configure offline mode
  * Set the `voldeloom.refreshDependencies` system property, project property, pass `--refresh-dependencies` to Gradle, or configure `refreshDependencies` in the extension to configure refreshDependencies mode
  * These are different from the global Gradle flags so that you can force Voldeloom to re-remap its derived artifacts, but not cause Gradle to do a bunch of redownloading too

# 2.0 (`agency.highlysuspect:voldeloom:2.0`)

Initial release of my fork.