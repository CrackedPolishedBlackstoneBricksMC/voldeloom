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

* `sourceClassName`: The class to diff against.
* `targetClassName`: Same as `sourceClassName`.
* `checksum`: ADLER-32 hash of the vanilla class, before patches are applied.
  * Forge checks this unless `-Dfml.ignorePatchDiscrepancies=true` before applying the patch, so it doesn't corrupt some class it doesn't expect.

If the `exists` field is `false`, patchBytes is still a sequence of gdiff instructions, but the sequence doesn't contain any COPY commands. This means the patch never looks at anything from the input file, which can be an empty 0-byte stream. This feature is sometimes used when Forge adds a new inner class (e.g. switchmaps).

* `sourceClassName`: The empty string(?)
* `targetClassName`: The class this binpatch creates.
* `checksum`: Not present in the file.

And fields common to both modes:

* `name`: Kind of strange, since the `.binpatch` file already *has* a filename...?
* `patchLength`: The length, in bytes, of the rest of the file.
* `patchBytes`: The actual sequence of gdiff instructions.

TODO: document the name/className parameters more

## And the patch data?

It's simply a [gdiff file](https://www.w3.org/TR/NOTE-gdiff-19970825.html).