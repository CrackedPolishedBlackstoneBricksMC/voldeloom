tl;dr I want to make the "providers" mostly an implementation detail of how the plugin scaffolds minecraft, for

* there isn't really a single ""the" remapped jar" in all cases (like 1.2.5craft, where there are two)
* just mental encapsulation purposes. i dont want this to worm its little tendrils all over the code

Right now theres a "get" method on providergraph, but i intend to remove it and expose only more granular/specific things to other parts of the plugin. but i have to catalog what is used where

## LoomGradlePlugin

* wants the path to the mapped jar (so it can compute the path to the linemapped jar and sources jar, for genSources). uses `Remapper`
  * 🚨WEE WOO🚨 WEE WOO🚨🚨 that is not 1.2.5 safe

## ShimResourcesTask

* causes `AssetDownloader` to actually download the assets (because theyre not needed just to compile !!)

# or to put that another way

## `Remapper` "the mapped jar" or "the intermediary jar" which is maybe not 1.2.5 clean

* LoomGradlePlugin wiring up gensources tasks

## `VanillaDependencyFetcher` asset index

(runconfig will go here soon, too)

## `AssetDownloader`

* asset task

# plan

* assets: move them to an asset downloading task, that's depended on in the regular client setup task workflow
* tinyfile: move the path to it up
* parsed tiny mappings: i can move the path up as well, might be worth it to just remove this and parse from scratch every time too lol. (memory profile this)

### Okay im sleepy now! good night