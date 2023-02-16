tl;dr I want to make the "providers" mostly an implementation detail of how the plugin scaffolds minecraft, for

* there isn't really a single ""the" remapped jar" in all cases (like 1.2.5craft, where there are two)
* just mental encapsulation purposes. i dont want this to worm its little tendrils all over the code

Right now theres a "get" method on providergraph, but i intend to remove it and expose only more granular/specific things to other parts of the plugin. but i have to catalog what is used where

## LoomGradlePlugin

* wants the path to the mapped jar (so it can compute the path to the linemapped jar and sources jar, for genSources). uses `Remapper`
  * 🚨WEE WOO🚨 WEE WOO🚨🚨 that is not 1.2.5 safe 
* wants the non-native library jars (so it can add them to the genSources classpath. uses `VanillaDependencyFetcher`

## RunConfig

* `cook` wants `VanillaDependencyFetcher` so it can find the natives directory to set `java.library.path`

## MigrateMappingsTask

* wants the current mappings in-memory as a TinyTree so it calls up `Tinifier`
* wants the mapped jar and intermediary jar for the remapper classpath, so it calls up `Remapper`
  * 🚨WEE WOO🚨 WEE WOO🚨🚨 that is not 1.2.5 safe

## RemapJarTask

* the in-memory mappings from `Tinifier`

## ShimResourcesTask

* causes `AssetDownloader` to actually download the assets (because theyre not needed just to compile !!)

## FernFlowerTask

* the mappings *file* from `Tinifier`

## GenDevLaunchInjectorConfigsTask

you know what, why do i still even have this task

* the path to the asset index (`VanillaJarFetcher`)
* the natives directory (`VanillaDependencyFetcher`)

## SourceRemapper

does tis one even work

* in-memory mappings from `Tinifier` can you believe it. remapping task wants mappings. wild world out there
* mapped/intermediary jars for the classpath (`Remapper`)

# or to put that another way

## `Tinifier` in memory tinytree

* MigrateMappingsTask
* RemapJarTask
* SourceRemapper

## `Tinifier` tinyfile

* FernFlowerTask

## `Remapper` "the mapped jar" or "the intermediary jar" which is maybe not 1.2.5 clean

* LoomGradlePlugin wiring up gensources tasks
* MigrateMappingsTask
* SourceRemapper

## `VanillaDependencyFetcher` non-native libraries

**this is still intentionally returning an empty list and honestly i havent seen many problems yet, lmao**

* LoomGradlePlugin genSources classpath

(theyre also put on dependency remap classpath, in providergraph)

## `VanillaDependencyFetcher` native libraries folder

* RunConfig cook
* GenDevLaunchInjectorConfigsTask

## `VanillaDependencyFetcher` asset index

* GenDevLaunchInjectorConfigsTask

(runconfig will go here soon, too)

## `AssetDownloader`

* asset task

# plan

* assets: move them to an asset downloading task, that's depended on in the regular client setup task workflow
* version manifest stuff: don't see anything wrong w/ just exposing this
* same for natives path and stuff
* tinyfile: move the path to it up
* parsed tiny mappings: i can move the path up as well, might be worth it to just remove this and parse from scratch every time too lol. (memory profile this)

### Okay im sleepy now! good night