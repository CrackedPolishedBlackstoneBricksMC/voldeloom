1.7.10 isnt loading sounds at all

try `net.minecraft.client.audio.SoundHandler.onResourceManagerReload`. this looks for sounds.json files over all registered namespaces. breakpoints are weird because they're non-resugared enhanced fors.

eventually this steps into `net.minecraft.client.resources.DefaultResourcePack.resourceExists` called with resource location `minecraft:sounds.json`, which attempts to load it from one of two places

* from the jar itself (getResourceAsStream)
* from `field_152781_b` which is an empty map

So the load fails and we have no sounds.json

`field_152781_b` is set through the constructor and never mutated, the map is constructed from `ResourceIndex` class which browses objects and index.json files... So it's a red flag that the map is empty i think...

Resourceindex is called with two parameters, one (param 7) is a filepath pointing to the `objects` folder (i pass that from AssetDownloader in gradle), another (param 12) is a string. assetindex will look in `new File(var1, "indexes/" + var2 + ".json");` to find the index.

var2 is set fom the minecraft constructor in `net.minecraft.client.main.Main.main` and is controlled by the `--assetIndex` parameter :tada: :tada: there it is !!

so theres 2 problems here

* i shouldnt pass `objects` in var1, i should pass the folder one level up
* var2 is `null` and so exits early before scanning the resource index