# binpatches.pack.lzma

In 1.6, Forge stopped being a jarmod. [Mojang was happy with this](https://github.com/MinecraftForge/FML/wiki/FML-and-the-new-launcher-in-1.6#on-some-inanity-seen-elsewhere):

> With the advent of the new launcher and other API stuff in the pipe, we expect the number of mods shipping base classes to drop significantly however, **and Mojang has politely asked that FML and MinecraftForge, jointly the largest platform shipping base classes, to cease once the new launcher is established**, a request with which I am happy to comply.

Instead, patches are done in a mildly less copyright-infringing way. The class `cpw.mods.fml.common.patcher.ClassPatchManager` handles the patches; `setup` loads them.

* Inside Forge, there's a file `binpatches.pack.lzma`
* This is LZMA-encoded `binpatches.pack`
  * The mystery library "lzma-0.0.1.jar" is used to extract this.
  * Apparently it comes from MCP (1.6.4 FML expects it inside the MCP home dir), but it's not anywhere to be found in `mcp811.zip`.
  * It's on [Spongepowered maven](https://repo.spongepowered.org/service/rest/repository/browse/maven-public/lzma/lzma/0.0.1/)
    * where the POM says it's "lwjgl's lzma.jar" and points [here](https://github.com/LWJGL/lwjgl/tree/master/libs)
      * [history of this file](https://github.com/LWJGL/lwjgl/commits/master/libs/lzma.jar)
      * user `matzon` [commited the first version of the file](https://github.com/LWJGL/lwjgl/commit/763b163ee618c6c7fcfa848a9c1063a34548fd33) in 2008
      * The commit also includes the LZMA SDK license
      * So they're either the author of the jar, or borrowed the code from somewhere else and that was the license? Or felt like including the sdk license was required for any code written with reference to the sdk
        * current versions of the [LZMA SDK](https://7-zip.org/sdk.html) do not include anything that looks like `lzma-0.0.1.jar`
  * Wow I got sidetracked
* `binpatches.pack` is a `Pack200`-encoded jar
  * Lovely, pack200 has been removed from the jdk
  * Just exploring on my pc it looks like there's an `unpack200` tool from java 8. Lol
  * im just gonna do a quick `unpack200 binpatches.pack binpatches.jar`
* `binpatches.jar` contains a `binpatch/client` and `binpatch/server` directory
* In each directory there are files with names like `net.minecraft.block.Block.binpatch`, `net.minecraft.block.BlockBaseRailLogic.binpatch`, etc
* At runtime, Forge picks the `client` or `server` directory
* Deltas are applied with a modified version of `com.nothome.delta`, algorithm is the same but the library was modified to not use Trove
  * the algorithm is called "GDiff"

## Parsing pack200

pack200 is an obscure, complex, and highly domain-specific compression scheme for Java archives, dating back to the applet days when download sizes were a big concern. It has been removed from the jdk so you need a third party decompressor such as Apache Commons Compress.

It was probably not the best compression scheme to use - most of its complexity is about ways to compress class files, not resource files like these `.binpatch`es - but it's what we got!

For a while commons-compress's pack200 parser was broken, giving you errors like `Failed to unpack Jar:org.apache.commons.compress.harmony.pack200.Pack200Exception: Expected to read 48873 bytes but read 3274`. See https://github.com/apache/commons-compress/pull/360 . You can fix it by wrapping the input in an `InputStream` that returns `false` from `markSupported`, and returns any nonzero value from `available`, [like this](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom/blob/10cb0c2f1d51c0570902e16d410868114baaba03/src/main/java/net/fabricmc/loom/mcp/BinpatchesPack.java#L63-L92). The bug has been fixed so I removed the fix from voldeloom.

# Binpatches themselves

The `.binpatch` file format is defined in terms of `DataInputStream`:

|           field | read with                                           |
|----------------:|:----------------------------------------------------|
|            name | `readUTF`                                           |
| sourceClassName | `readUTF`                                           |
| targetClassName | `readUTF`                                           |
|          exists | `readBoolean`                                       |
|        checksum | if "exists", call `readInt`; else 0                 |
|     patchLength | `readInt`                                           |
|      patchBytes | `readFully` into a buffer the size of `patchLength` |

If the `exists` field is `true`, `patchBytes` is a sequence of [gdiff instructions](https://www.w3.org/TR/NOTE-gdiff-19970825.html) that transforms a vanilla class into a patched class.

If the `exists` field is `false`, patchBytes is still a sequence of gdiff instructions, but the sequence doesn't contain any COPY commands; i.e. the patch never looks at anything from the input file. A patch with `exists = false` describes the difference *from* a zero-byte file *to* the destination.

* `name`: Internal name of the patch. Afaik this is *only* used for debugging in `ClassPatch#toString`.
  * It is *usually* similar to `sourceClassName` but it shouldn't br relied upon for this purpose.
* `sourceClassName`: The proguarded name of the class to diff against.
  * It is in 'package' format (dots separate packages).
  * When Forge's classloader attempts to load a class with a name `xyz`, it will look for binpatches whos `sourceClassName` is `xyz`.
  * (So the `sourceClassName` is still relevant for `exists = false` classes.)
* `targetClassName`: The *MCP mapped* name of the class to create, in 'package' format.
  * Used mainly as another layer of insurance that the patch is applied to the correct class. (Launchwrapper `IClassTransformer` passes in both the proguarded & mapped names, and forge checks both.)
  * The patch *itself* does not create a class with this name. It creates a class with the proguarded name.
* `exists`: As above.
* `checksum`:
  * If `exists = true`, this is the ADLER-32 hash of the bytes of the *vanilla* class *before* applying the patch.
  * Forge refuses to perform the patch (unless `-Dfml.ignorePatchDiscrepancies=true`) unless the hashes match.
  * If `exists = false`, this field is not present in the file at all.
* `patchLength`: The length, in bytes, of the rest of the file.
* `patchBytes`: The actual sequence of gdiff instructions.

## Trivia

* Forge reads either the client or the server patchset based on directory. The client reads from `./binpatches/client/` and the server reads from `./binpatches/server`.
* Even within the same patchset, it's possible for multiple patches to exist for a given source file.
  * Patch order is based off encounter order in the zip.
  * Forge does not use this feature.
* Why are `exists = false` patches used?
  * Sometimes Forge "patches off a `@SideOnly` annotation" with `exists = false` binpatches.
  * Sometimes forge patches *in* an incorrectly-sided `@SideOnly` class. Weird. One exists under `net.minecraft.client.ClientBrandRetriever`.
  * Switchmaps, of course. 

## And the patch data?

It's simply a [gdiff file](https://www.w3.org/TR/NOTE-gdiff-19970825.html).