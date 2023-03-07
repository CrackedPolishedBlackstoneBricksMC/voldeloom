# how does `loom.layered` work in loom 1

* The actual `loom.layered` function is defined [here](https://github.com/FabricMC/fabric-loom/blob/83b968df64845b3a1183e51cb9bbed5579089585/src/main/java/net/fabricmc/loom/extension/LoomGradleExtensionApiImpl.java#L194-L204). It

* creates a `LayeredMappingSpecBuilder`
* allows the script to configure this builder by adding "layers" (see `LayeredMappingSpecBuilderImpl` for the DSL)
* returns a new `LayeredMappingsDependency`, passing in the built layers. This is a `SelfResolvingDependency, FileCollectionDependency` that, at the end of the day, `resolve`s to one tinyfile
  * if the file does not exist, it realizes each mapping layer, then visits each one in turn (in `LayeredMappingProcessor`)
  * each layer may add, modify, or remove mappings at will
  * (it uses mappingio)

This dependency gets put into the `mappings` configuration through the buildscript, no magic, it's just because you use the method like `dependencies { mappings loom.layered(...) }`.

What is `GradleMappingsContext`

* various methods for obtaining relevant file paths inside the gradle cache, downloading files, resolving deps from a maven coordinate...
  * for example `MojangMappingsSpec` uses it to locate a `mojang` folder inside the gradle cache to download to, and for spinning up the download builders
  * huh since when did loom have a very voldeloom-ish download builder. convergent evolution?
* Idk if this is suuuper necessary, for example they use the "correct" `ScopedSharedServiceManager` gradle utility, but the convention in this project to just ignore gradle best practices and mutate shit in `afterEvaluate` tbh

what kinds of layers exist? here checking `LayeredMappingSpecBuilderImpl` methods

* `officialMojangMappings` -> `MojangMappingsSpecBuilderImpl`
  * builder option for `nameSyntheticMembers` (affects the parser later)
  * realizes as a `MojangMappingsSpec`
    * that downloads the client and server mappings proguard txts and returns a `MojangMappingLayer`
      * that visits the `MappingVisitor` with a proguard mappings parser
* `parchment` -> `ParchmentMappingsSpecBuilderImpl` (requires a `FileSpec` to construct it)
  * builder option for `removePrefix`
  * realizes as a `ParchmentMappingsSpec`
    * that immediately returns a `ParchmentMappingLayer`
      * that parses the parchment json provided in the zip/jar provided to the `FileSpec` and visits `MappingVisitor` with parameter names
* `signatureFix` -> no builder to configure (requires a `FileSpec` to construct it)
  * realizes as a `SignatureFixesSpec`
    * that immediately returns a `SignatureFixesLayerImpl`
      * this doesn't visit the MappingVisitor yet, but parses json provided at `extras/record_signatures.json` in the zip/jar provided to the `FileSpec` and populates the `getSignatureFixes` method of `MappingLayer`
      * not sure what this is for but it looks like some modern-java pensivewobble stuff
* `mappings` -> `FileMappingsSpecBuilderImpl` (requires a `FileSpec` to construct it)
  * builder options include:
    * `mappingPath` (path to the tinyfile inside the jar)
    * `fallbackNamespaces` (mappingio stuff i think)
    * `enigmaMappings` (mappingio tries to auto-detect various mapping formats, but it needs help detecting the enigma format, so as a user you need to switch it on manually)
    * `containsUnpick` (looks for `extras/unpick.json` and `extras/definitions.unpick` files, passes them to some unpick machinery later in the plugin)
    * `mergeNamespace` (mappingio stuff i think)
  * realizes as a blah blah, basically configures a mappingio reader the way you requested and visits the mappingvisitor with it
* "Intermediary is always the base layer"
  * you always get one `IntermediaryMappingsSpec` for free, nothing to configure, returns `IntermediaryMappingLayer`
    * its mappingio stuff

quick sidenote, what is a `FileSpec` above? it's basically a Loom DSL for single-file dependencies, if the argument looks like a URL it will be parsed as a URL, if the argument looks like a gradle dependency it will return the gradle dependency (and when resolving, will expect to receive exactly one file in the dependency)

## Okayyy, so, how might this be adapted

* I did not know about `SelfResolvingDependency`, that's *extremely* convenient
  * I thought there'd have to be crap sprayed all over the mappings system
* The current mappings system treats the srg, fields.csv, and methods.csv files pretty much separately (see `MappingsWrapper`). This is authentic to how MCP works and I'd like to keep doing that, if possible, because it means i can use all-authentic file formats (reducing the risk of a buggy/lossy converter messing up mappings)
  * Problem: there are more files to keep track of (joined or client/server.srg, fields.csv, methods.csv, packages.csv?, params.csv?) compared to tinyv2 which encompasses all of those ideas, and `mappings` is only one configuration.
    * Requiring users to type `mappingsClasses` `mappingsMethods` configs would suck.
    * one solution: the "canonical" mappings format used in voldeloom is a .zip where each relevant file can be found somewhere inside (at any location, so unmodified forge sources zips happen to be valid under this format)
    * bad solution: allow multiple files in the configuration, loop over all files and try to guess what they are from their filename or contents (meh)
* A simple "mutate what you want" visitor pattern should suffice, i don't thing the mappingio ecosystem is worth buying into

I would like to have some mechanism to hash the layout of a `layered`-pipeline so you don't have to cachebust when you change it 

*Eventually*, if possible, i would like to completely avoid using tinyfiles in the project - rn they're used for tiny-remapper (ofc) and for gensources comments and maybe a few other places, but i don't benefit much from the tiny format specifically