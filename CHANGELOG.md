Running changelog document, will be added to as I commit things.

# Next version: 2.1 (`agency.highlysuspect:voldeloom:2.1-SNAPSHOT`)

## Breaking changes

* Changed the Gradle extension's name from `minecraft { }` to `volde { }`.
  * Preparation for adding some more free-functions to the extension (it'd be weird if you referred to them with `minecraft`)
  * Possibly preparation for "getting out of the way of other extensions", so you can use it and another Minecraft plugin in the same project? Maybe?
  * Echoes what Loom did - the name is more accurate
* Moved `srgsAsFallback`.
  * Replace it with `forgeCapabilities { srgsAsFallback = true }`.
* Adjusted file paths to some intermediate products, to be more consistent:
  * Forge's libraries are downloaded to a folder containing the complete Forge artifact name, not just the version
  * Mappings have one canonical name, which also contains the complete mappings artifact name (instead of ad-hoc gluing the artifact id and version together in a slightly different way every time)
  * Mapped Minecrafts, as well as -sources and the like, are in `(cache dir)/mapped/(mappings name)/(...).jar`.
  * **(idk if this is breaking)** - Mapped Minecraft is added to the project using a file dependency instead of a `flatDir` dependency.
  * **(workspace-breaking)** - You may need to delete files using the old naming convention from `./run/coremods`.
* The hack that erased some classes from the mappings so Ears 1.4.7 would work in deobf is removed
  * Replaced with `hackHackHackDontMapTheseClasses` property inside `volde`
  * As the name suggests :tm: i will remove it later when i gt the good mappings system up

## New

* Small system for "changing behavior of the plugin based on what era of Forge you're using".
  * Configure this with `volde { forgeCapabilities { /* ... */ } }`.
  * Set `distributionNamingScheme` to pick which naming scheme your mods are remapped to.
  * Set `srgsAsFallback` to assert that this Forge version maps everything to SRG names; don't set it to assert that unmapped names show through as proguarded names.
  * If you don't set them, they will be guessed from the Minecraft version and Forge version:
    * `distributionNamingScheme` is `intermediary` since 1.5 and `official` before it
    * `srgsAsFallback` is `false` since 1.5 and `true` before it
* Rudimentary custom access transformers.
  * Specify dependencies in the `accessTransformers` configuration, e.g. `accessTransformers files("./src/main/resources/my_ats.cfg")`.
  * **(currently you will need to refresh dependencies to force-update artifacts derived from it... sorry)**
  * The plugin will *not* create a Forge coremod for you that will cause ATs to be found in production - you must do this yourself.
  * The plugin will *not* retrieve ATs transitively from mod dependencies.
    * Forge has them configurable through Java in the coremod, so it's impossible in the general case.
    * Just paste them into the project. (Because I don't try to auto generate a coremod, you don't have to worry about them showing up in the built jar)

## Other changes

* The project-wide `offline` and `refreshDependencies` flags were moved from `Constants` into the extension:
  * Set the `voldeloom.offline` system property, project property, pass `--offline` to Gradle, or configure `offline` in the extension to configure offline mode
  * Set the `voldeloom.refreshDependencies` system property, project property, pass `--refresh-dependencies` to Gradle, or configure `refreshDependencies` in the extension to configure refreshDependencies mode
  * These are different from the global Gradle flags so that you can force Voldeloom to re-remap its derived artifacts, but not cause Gradle to do a bunch of redownloading too

# 2.0 (`agency.highlysuspect:voldeloom:2.0`)

Initial release of my fork.