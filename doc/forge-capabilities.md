Minecraft Forge is an evolving piece of software and versions for older Minecrafts don't always have the same featureset as versions for newer Minecrafts.

Voldeloom contains a handful of internal toggle-switches for its behavior. Their default value is guessed from the current Minecraft version. At the `--info` log level, a message will be logged whenever it makes a guess. 

They are toggleable yourself, if it guessed wrongly or if you're using a custom Forge version. Configure as such:

```groovy
volde {
	forgeCapabilities {
		srgsAsFallback = true
		//...
	}
}
```

They are:

*`minecraftRealPath`* - `Function<Path, Path>` - Certain versions of Minecraft work out of a subdirectory. 1.2 and earlier appends `minecraft` (on Mac) or `.minecraft` (windows/linux), and later versions use the path as-is.

*`classFilter`* - `Set<String>` - Minecraft used to contain shaded copies of various open-source libraries that MCP tried to remap. The period-accurate Forge installation procedure would sometimes delete these libraries after applying MCP names. The exact libraries changed over time. From versions 1.2 to 1.7 this set contains `argo` and `org`; other versions use an empty set.

*`bouncycastleCheat`* - bool - Minecraft Forge for 1.3 seems to depend on the same version of Bouncycastle that Forge for 1.4 does, but doesn't declare it anywhere. **TODO: I'm not sure whether Bouncycastle ended up on the classpath with a different method I should emulate instead?**

*`libraryDownloaderType`* - `net.fabricmc.loom.ForgeCapabilities.LibraryDownloader` - Some versions of Minecraft Forge would attempt to connect to a hardcoded, now long-dead URL, download additional Java libraries, and stick them on the classpath before starting the game. If set to `DEAD` (1.4-), Voldeloom will take steps to avoid invoking the library downloader. If set to `CONFIGURABLE` (1.5), the library downloader can be configured through the `fml.core.libraries.mirror` system property. And if set to `NONE` (1.6+), Forge is assumed not to have one.

*`distributionNamingScheme`* - string - the way references to fields and variables are encoded in reobfuscated release mod jars. This is `"intermediary"` since 1.5 and `"official"` before it.

*`requiresLaunchwrapper`* - bool - Minecraft 1.6 changed its launch procedure to require LegacyLaunch (aka Launchwrapper); a different method of setting up run configurations is required.

*`supportsAssetsDir`* - bool - If `true` (1.6+), the game accepts an `--assetsDir` argument to set the path of the asset directory. If `false` (1.5-), the game uses an assets directory in a hardcoded path inside the game folder, and Voldeloom must copy assets into that folder before starting a client run configuration.

*`mappedAccessTransformers`* - bool - If `true` (1.7+), access transformer files have SRG-named access transformers. If `false` (otherwise), they're proguarded.

~~*`srgsAsFallback`* - bool - when a field or method is missing an MCP name, if `true` (1.5+) the proguarded name will show through, and if `false` (1.4-) the SRG will show through. This is relevant if you need to use reflection or a coremod to access a field.~~ Removed in Voldeloom 2.4; effectively always evaluates to "true". If you'd like to do reflection, you'll need to look up the proguarded name yourself.

(All options also have a form suffixed with `Supplier` (like `srgsAsFallbackSupplier`) that let you provide a `Supplier<T>` instead of a T. See also: `net.fabricmc.loom.util.Suppliers.memoize`.)