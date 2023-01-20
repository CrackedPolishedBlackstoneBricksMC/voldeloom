ok what is actually in these various files.

# `mcp726a.zip`, `conf/` directory

## `client.srg` and `server.srg`

the "internal names" format is always used (net/minecraft/blah, not net.minecraft.blah)

### lines starting with `PK: `

might define some sort of package mapping? or something? its weird

### lines starting with `CL: `

define a class mapping. first proguarded name, then named name.

> `CL: a net/minecraft/src/CrashReport`

this version of mcp has 1,408 of them in the client

### lines starting with `FD: `

fields. always given as "[class]/[field name]".

> `FD: a/a net/minecraft/src/CrashReport/field_71513_a`

this version of mcp has 5,350 of them in the client

### lines starting with `MD: `

methods. first parameter is input name (same format as `FD`, joining the class and method names with `/`), second parameter is input method descriptor, third and fourth parameters are the same thing as the first and second, but mapped.

> `MD: a/a ()Ljava/lang/String; net/minecraft/src/CrashReport/func_71501_a ()Ljava/lang/String;`

another one:  
> `MD: abb/d (II)Lzz; net/minecraft/src/ChunkProviderGenerate/func_73154_d (II)Lnet/minecraft/src/Chunk;`

this method descriptor contains a class also from minecraft `Lzz;` - you can see how it gets remapped to `Lnet/minecraft/src/Chunk;`. so yeah its kinda "redundant" information

this version of mcp has 9,386 of them in the client

## `newids.csv`

MCP dates from before the merge in 1.3 so it was designed with machinery to separately map the client and server. if `client.srg` says that a method is `field_1662_b`, and `server.srg` says the method is `field_20154_b`, `joined.csv` says "hey actually, can you refer to this as `field_77597_b` when you join the jars? thanks".

fields and funcs share the same file. the first line is a csv header, reading `client,server,newid`, and an `*` in an entry means the field or method doesn't exist on that side.

In this version of MCP it looks like newids is mostly vestigial. The client and server srgs directly map into newid-numbered names. Newids start at 70000, so any srg name with a lower number is an oldid.

Technically there's five naming schemes to worry about: client proguard, server proguard, client srg oldids, server srg oldids, joined srg newids? In practice you can probably into newids as you parse the srg because oldids aren't needed anywhere.

### `fields.csv`

CSV with the following header: `searge,name,side,desc`.

The `searge` column is the (newid) field name, like `field_70009_b`. Note that the class name is not included because srg field names are unique.

`name` is, of course, the mapped name of the field. 

`side` I'm not sure about. It is either `0` or `1` and the obvious guess is that it could be related to whether the field originates from the client/server jar, but there's a good mix of fields from both sides in both categories, pretty sure. Hm.

`desc` is a simple plaintext Javadoc. Some of them contain useful nuggets:

> `field_73442_b,inventorySlot,0,The clicked slot (-999 is outside of inventory)`

### `methods.csv`

exactly the same format as `fields.csv`, but for methods! like, it's literally the same thing but everything starts with `func_` instead of `field_`.

### `joined.exc` and `params.csv`

`joined.exc` is an an "exceptor" file, used by the mcinjector tool. it looks like its main purpose is assigning... srg parameter names?

> `net/minecraft/src/BlockAnvil.func_82519_a_(Lnet/minecraft/src/World;IIII)V=|p_82519_1_,p_82519_2_,p_82519_3_,p_82519_4_,p_82519_5_`

i'll let params.csv speak for itself:

```csv
param,name,side
p_70000_1_,par1PlayerUsageSnooper,0
p_70001_1_,par1PlayerUsageSnooper,0
p_70003_1_,par1,0
p_70003_2_,par2Str,0
p_70004_1_,par1Str,0
p_70004_2_,par2ArrayOfObj,0
p_70006_1_,par1Str,0
p_70011_1_,par1,0
p_70011_3_,par3,0
```

very High quality Full hd 1080p parameter name mappings :ballot_box_with_check: definitely not 99.9999% auto generated names lol.

### the rest

`version.cfg` -> ini-format file that includes a bit of version information about mcp and the minecraft versions it's for

`astyle.cfg` -> config file for the old Artistic Style java formatter! it's used to postprocess the output in the source-based world just to have something prettier in your IDEs.

`mcp.cfg` -> ini-format file with a million config entries that are relevant to the original mcp scripts

`patches/` -> various source patches to correct for deficiencies in Fernflower that caused the code to fail to recompile. There's also `Start.java`, which is patched *in* to the game to set the run directory outside of `.minecraft` before invoking the real main.

# `forge-1.4.7-6.6.2.534-src.zip`, `forge/fml/conf/` directory

`client.srg` and `server.srg` have been replaced with a single `joined.srg`. this still maps every class to the `net/minecraft/src` package structure.

the `side` column in `fields.csv` and `methods.csv` still exists, but it is always `2` instead of `0` or `1`. your guess is as good as mine

a new file exists, `packages.csv`, that mostly speaks for itself. it augments the contents of `joined.srg` with packaging information.

```csv
class,package
Block,net/minecraft/block
BlockAnvil,net/minecraft/block
BlockBeacon,net/minecraft/block
BlockBed,net/minecraft/block
```

since MCP maps everything to `net/minecraft/src` all the class names are already guaranteed unique :) so this only includes the destination package name.

# tl;dr of minecraft forge 1.4.7

* `fields.csv` and `methods.csv` -> `searge,name,side,desc`
  * `searge` newid searge name (guaranteed unique) like `field_12345_a`
  * `name` mapped name
  * `side` (forge) the number two, (mcp) the number zero or one without any seeming rhyme or reason
  * `desc` a javadoc or note

* `joined.exc` and `params.csv`
  * ParchmentMC if it sucked

* `joined.srg`
  * `PK:`: Hey Guys. Did You Know That the mapped name of the `net` package, is `net` Subscribe for more tips
  * `CL:`: proguard class names to NMS mapped names (always starting with net/minecraft/src) holy shit is this where the bukkit term nms comes from....... real
  * `FD:`: field names
  * `MD:`: method names and descriptors

* (Forge only) `packages.csv` -> `class,package`
  * augments the `CL:` class mappings with packaging data

* `newids.csv` -> `client,server,newid`
  * vestigial because `joined` already maps into newids

# `forge-1.3.2-4.3.5.318-src.zip`, `forge/fml/conf/` directory

It's the same as forge 1.4.7 but there is no `packages.csv` yet.

# `forge-1.5.2-7.8.1.738-src.zip`, `forge/fml/conf/` directory

looks the same as forge 1.4.7 at first blush.

(it's worth mentioning that forge 1.5 is when mods started being distributed with their fields/methods in SRGs, instead of proguarded.)

# `forge-1.6.4-9.11.1.1345-src.zip`, `forge/fml/conf/` directory

`joined.srg` annotates each line with a `#C` or `#S` if they're client-only or server-only. classes, fields, methods, even the weird packages thing are all annotated.

> `CL: abs net/minecraft/src/ColorizerFoliage #C`

other than that i don't see much different.

# (the one i've been putting off cause i think it'll be wacky) `forge-1.2.5-3.4.9.171-src.zip`, `forge/conf` directory

the directory is different from forge 1.4.7, no `fml` in the path.

real client/server split! what ramifications does this have!

* There is no `newids.csv` because the client and server are not merged
* there is no `joined.srg`, instead there are split `client.srg` and `server.srg`s. the format of the SRGs looks identical to the usual.
* there is no `joined.exc`, instead there are split `client.exc` and `server.exc`s. params are still found joined, in `params.csv`.

i think in the fields and methods csvs, side `0` and `1` actually map to the client and server better now lol???

thinking about how funny this section must sound to someone who was around for the 1.3 merge, hearing me describe it going back in time, as a 1.2 split

# (out of curiosity) 1.7.10

ok so the `src.zip` on their Maven is actually the mod development kit. whoops. looking on github instead, looks like the file path is only `fml/conf/`, the `forge` shell was added by their release process

new file! new file! [`exceptor.json`](https://github.com/MinecraftForge/MinecraftForge/blob/1.7.10/fml/conf/exceptor.json) is a *huge* (9k lines) json file with inner class information

* keys of the top-level json map are (packaged) class names
* i see `innerClasses` keys that list off child classes
* i see `enclosingMethod` keys for lambdas and anonymous classes, that list off the owner of the method it was defined in (not the method itself oddly enough)
* didn't find any more keys with a quick scroll though

^ DEFINITELY investigate this more

`joined.exc` contains srg parameter names as usual, but also mappings to `CL_00000633`-thingies, probably related to the numbers in exceptor.json

`fields.csv` looks the same but aw shucks there's now `0` `1` *and* `2` showing up in `side`

`joined.srg` doesn't contain the `#C` `#S` tags that 1.6.4 had (interesting), and it also maps directly to classes with full package names, so the `packages.csv` file is unnecessary and was removed.

this is about when ForgeGradle enters the picture btw.

# (really out of curiosity now) 1.8

at this point we start watching the death of FML as a separate project from Forge.

1.8.0 used [this](https://github.com/MinecraftForge/FML/tree/d4ded9d6e218ac097990e836676bbe22b47e5966) FML submodule. curiously, all CSV files are gone without replacement. there is no way to find named field/method names inside the `conf` directory anymore. (on the bright side, `exceptor.json` actually tells you which method `enclosingMethod`s belong to, not just the class that that method belongs to. so thats something)

by 1.8.8, the FML submodule is gone and its java classes were merged into `src/main/java/net/minecraftforge/fml`. mappings are obtained somewhere else now. we start to venture into Modern Forge, well utside the scope of this project.

> FML is no more. FML has ceased to be. FML's expired and gone to meet its maker. FML's a stiff! Bereft of life, FML rests in peace.
> 
> [-cpw](https://github.com/MinecraftForge/MinecraftForge/commit/614bbcb0da8b8bcb9fd49cc70cc6856be4f49a7c)