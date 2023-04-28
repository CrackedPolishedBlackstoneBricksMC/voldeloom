Here are the things you can put in the `volde` block in your buildscript. Also see `LoomGradleExtension.java` in the source code.

```groovy
volde {
	warnOnProbablyWrongConfigurationNames = true
	customManifestUrl = null
	librariesBaseUrl = "https://libraries.minecraft.net/"
	fmlLibrariesBaseUrl = "https://files.prismlauncher.org/fmllibs/"
	resourcesBaseUrl = "https://resources.download.minecraft.net/"
	
	autoConfigureToolchains = true
	setDefaultRunToolchainVersion(JavaVersion.VERSION_1_8)
	setDefaultRunToolchainVendor("ADOPTIUM")
	
	offline = false
	refreshDependencies = false
	
	runs {
	
	}
	
	forgeCapabilities {
		//...
	}
	
	beforeMinecraftSetup {
		//...
	}
	
	afterMinecraftSetup {
		//...
	}
}
```

## `warnOnProbablyWrongConfigurationNames`

Voldeloom adds a few configurations, and it tries to make the names match Gradle's convention. There is no technical reason for this - I can name the configurations whatever - but it's done for consistently with the stock Gradle Java plugin configuration names, which changed in Gradle 7 for some reason.

| Gradle version | Mod on compilation classpath and runtime | Mod only at runtime |
|---|---|---|
| < 7 | `modCompile`, `coremodCompile` | `modRuntime`, `coremodRuntime` |
| >= 7 | `modImplementation`, `coremodImplementation` | `modRuntimeOnly`, `coremodRuntimeOnly` |

Voldeloom will print a warning to the console when you try to use a configuration for a version it doesn't exist for, like `modCompile` on Gradle 7. This helps track down annoying bugs when updating the Gradle version of an old project. Additionally, it will warn when accessing the nonexistent configuration `coremodCompileOnly`, as it is not necessary (`modCompileOnly` is fine).

Setting `warnOnProbablyWrongConfigurationNames` to `false` will remove the warning messages, if you have a legitimate reason to use these configuration names.

## `customManifestUrl`

If nonnull, this URL will be contacted to download the Minecraft per-version manifest, instead of reading from `version_manifest.json`.

## `librariesBaseUrl` / `resourcesBaseUrl`

URL, including trailing `/`, that Minecraft's (native libraries/assets) will be downloaded from. Defaults to Mojang's official server.

## `fmlLibrariesBaseUrl`

URL, including trailing `/`, that acts as a mirror of Minecraft Forge's library-downloader site. This defaults to Prism Launcher's mirror.

## Toolchain stuff

If `autoConfigureToolchains` is `true`, Voldeloom will set Java toolchains on all run configs. It's overridable per-run config but the default Java version and vendor are settable with `setDefaultRunToolchainVersion` and `setDefaultRunToolchainVendor`.

## `offline` / `refreshDependencies`

`offline` will cause Voldeloom to error whenever it needs to download a file it doesn't have in the cache, rather than attempt to contact a web server. `refreshDependencies` will cause Voldeloom to ignore the contents of the cache (including both downloaded files & computed files) and recompute them from scratch.

These settings default to `true` if you pass `--offline` or `--refresh-dependencies` when invoking Gradle, but that will also set offline/refresh-deps mode for other mechanisms inside Gradle. These settings exist for telling only Voldeloom what to do.

## `runs` block

TODO: Document run configs (see the `RunConfig` class in the meantime)

## `forgeCapabilities` block

Forge and Minecraft have changed over the years and Voldeloom contains a bunch of conditionals dependent on the current era. Voldeloom tries to guess this information based on the Minecraft version, but if any switches need switching, you can do it here. See `forge-capabilities.md` for more information.

## `beforeMinecraftSetup` / `afterMinecraftSetup` blocks

Voldeloom does most of its work in an `afterEvaluate` block attached to the current Gradle project. You can also add your own `afterEvaluate` blocks to your buildscript, it's a normal feature of Gradle, but sometimes the ordering is inconvenient or unclear (will Voldeloom's block run first, or yours? is the ordering defined behavior? can the ordering be changed? honestly I don't know myself).

Voldeloom's `afterEvaluate` block looks like this:

* Early-exit if project configuration already failed (`project.getState().getFailure() != null`). For some reason Gradle still runs `afterEvaluate` blocks in that situation.
* Run all `beforeMinecraftSetup` blocks.
* Take care of all the other things Voldeloom does in its `afterEvalute` block.
* Run all `afterMinecraftSetup` blocks.