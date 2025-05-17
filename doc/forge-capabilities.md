Minecraft Forge is an evolving piece of software and versions for older Minecrafts don't always have the same featureset as versions for newer Minecrafts.

Voldeloom contains a handful of internal toggle-switches for its behavior. Their default value is guessed from the current Minecraft version. At the `--info` log level, a message will be logged whenever it makes a guess. 

They are toggleable yourself, if I guessed incorrectly or if you're using a custom Forge version. Configure as such:

```groovy
volde {
	forgeCapabilities {
		requiresLaunchwrapper = true
		//...
	}
}
```

All options also have a form suffixed with `Supplier` (like `requiresLaunchwrapperSupplier`) that let you provide a `Supplier<T>` instead of an immediate `T`.

The Forge capabilities are:

## `distributionNamingScheme`

A string.

The way references to Minecraft's fields and variables are encoded in release mod jars. This is `"intermediary"` since 1.5 ("SRG names"), and `"official"` before it.

## `classFilter`

A `Set<String>`.

Minecraft used to contain shaded copies of various open-source libraries that MCP tried to remap. The period-accurate Forge installation procedure would sometimes delete these libraries after applying MCP names. The exact libraries changed over time. From versions 1.2 to 1.7 this set contains `argo` and `org`; other versions use an empty set.

## `bouncycastleCheat`

A bool.

Minecraft Forge for 1.3 seems to depend on the same version of Bouncycastle that Forge for 1.4 does, but doesn't declare it anywhere. If `true` (1.3=), stick bouncycastle on the classpath like it's a Forge library even though Forge didn't declare it. **TODO: I'm not sure whether Bouncycastle ended up on the classpath with a different method I should emulate instead?**

## `requiresLaunchwrapper`

A bool.

Minecraft 1.6 changed its launch procedure to require LegacyLaunch (aka Launchwrapper); the game is launched through a different main class and a LegacyLaunch tweaker is used.

## `libraryDownloaderType`

A `net.fabricmc.loom.ForgeCapabilities.LibraryDownloader`.

Some versions of Minecraft Forge would attempt to connect to a hardcoded, now long-dead URL, download additional Java libraries, and stick them on the classpath before starting the game. A mirror of this service is hosted by the Prism Launcher folks at `https://files.prismlauncher.org/fmllibs/`.

* If set to `DEAD` (1.4-), Voldeloom will contact the Prism Launcher mirror and place the files where Forge expects to see them, so Forge will not attempt to contact the dead server.
* If set to `CONFIGURABLE` (1.5=), the library downloader can be configured through the `fml.core.libraries.mirror` system property, so Voldeloom will configure it to point at Prism's mirror and Forge will download its own libraries at runtime.
* And if set to `NONE` (1.6+), no library downloader is present so no actions need to be taken.


## `supportsAssetsDir`

A bool.

If `true` (1.6+), the game accepts an `--assetsDir` argument to set the path of the asset directory. If `false` (1.5-), the game uses an assets directory in a hardcoded path inside the game folder, and Voldeloom must copy assets into that folder before starting a client.

## `mappedAccessTransformers`

A bool.

If `true` (1.7+), access transformer files have SRG-named access transformers. If `false` (otherwise), they're proguarded.

## `minecraftRealPath`

A `Function<Path, Path>`.

Certain versions of Minecraft work out of a subdirectory. 1.2 and earlier appends `minecraft` (on Mac) or `.minecraft` (windows/linux), and later versions use the path as-is.

## `needsAsm4Compat`

Forge scans every class of every jar with ObjectWeb ASM in order to find `@Mod` annotations and build the `ASMDataTable`. This was done with ASM version 4 up through 1.6 and early 1.7 releases, and was changed to ASM version 5 later.

Classes containing features from Java 7 and later will crash ObjectWeb ASM 4 and cause Forge to have trouble inspecting the jar. This can not only caused by language features used in your mod (such as lambdas), but also things that `tiny-remapper` tends to add into jars (such as local variable information).

If `true` (1.6-), Voldeloom will run a simple `Asm4CompatClassVisitor` over everything it remaps to filter out line number tables, mid-method annotations, etc. It's not as fancy as RetroLambda or anything like that; it's just enough to get Forge to not choke on your class. If `false` (1.7+), this filtering is not performed.

# Obsolete stuff

## ~~`srgsAsFallback`~~

A bool.

~~When a field or method is missing an MCP name, if `true` (1.5+) the proguarded name will show through, and if `false` (1.4-) the SRG will show through. This is relevant if you need to use reflection or a coremod to access a field.~~

Removed in Voldeloom 2.4; effectively always evaluates to "true". If you'd like to do reflection, you'll need to look up the proguarded name yourself.
