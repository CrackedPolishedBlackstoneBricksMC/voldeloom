# Things that work

* Forge's Maven is configured for you with free bonus `metadataSources` forward-compat magic for gradle 5+
* Downloading Minecraft, merging the jars, remapping, etc (the usual from a minecraft toolchain)
* Patching Minecraft with Forge's classes:
    * 1.5 and below: jarmod style
    * 1.6/maybe 1.7: parse and apply `binpatches.pack.lzma` gdiff archive
* `genSources` (sans methods with MCP messed-up switchmaps), attaching sources in intellij, browsing and find usages, MCP comments in source code
* Asset downloading, in the old file layout that legacy versions use
* `runClient` gets in-game (on at least 1.4.7 and 1.5.2)
    * `shimForgeLibraries` task predownloads Forge's runtime-downloaded deps and places them in the location Forge expects, because the URLs hardcoded in forge are long dead
    * `shimResources` task will copy assets from your local assets cache into the run directory (because you can't configure `--assetsDir` in this version of the game)
* Recognized mapping sources: a Forge `-src` zip, an MCP download, tinyv2 archives (although tinyv2 will break binpatches)
* Mostly-complete backport of the "extendable run configs" thing from newer Fabric Loom versions
    * Define your own run configs, with per-config vm args and system properties and stuff
    * 1.4 doesn't parse any program arguments apart from the username (arg 0) and session token (arg 1)
* `modImplementation`/etc works
    * `coremodImplementation`/etc exists for coremods that exist at runtime, which need special handling (`remappedConfigEntryFolderCopy` task handles it)
* Gradle 4 and 7 work
    * On Gradle 7-, use `modCompile` instead of `modImplementation`, and drop the `only` from `modRuntimeOnly` (`implementation`/`runtimeOnly` are a gradle 7 convention)
* On Gradle 6+, a Temurin 8 toolchain is provisioned for run configs. You can configure the version and vendor
    * Done without breaking Gradle 4 source compatibilty in the plugin btw... so its kinda jank
* Access transformers (barely)
    * It's a bit buggy, you will need to `-Pvoldeloom.refreshDependencies` to get them to propagate through.
    * ATs will not be discovered from other mods (by design)
    * The plugin will not help you declare your ATs for the non-development workspace (yet)

# Things that don't work

* Generated IDE run configs are broken.
    * The `runClient` task works, and is more of a priority because I get *much* more control over the startup process
    * (I don't think many modders know what the actual difference between `runClient` and ide runs are, maybe i should write something up)
    * Something is putting a million java 8 jars on the runtime classpath and exploding 1.4.7
* I don't know how broken Eclipse/VSCode are
* Dependency source-remapping and `migrateMappings` have been removed.
* You can depend on other people's coremods, but you can't develop them (they don't end up in the `coremods` folder where Forge wants to find them)

See also [./known-differences.md](./known-differences.md).