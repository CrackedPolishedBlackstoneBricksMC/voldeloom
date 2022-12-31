cpw.mods.fml.relauncher.RelaunchLibraryManager discoverCoreMods

- makes `coremods` folder under the .minecraft dir
- basically only scans that folder weeee

Well that was a disappointingly short wild goose chase. Compare cpw.mods.fml.common.discovery.ModDiscoverer, which has methods for finding classpath mods and moddir mods. Coremod stuff happens wayyyy earlier in relaunchlibrarymanager i guess

A coremod in this version is anything that contains an `FMLCorePlugin` entry in its `META-INF/MANIFEST.MF` 

