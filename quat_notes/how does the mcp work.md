# how does period-accurace MCP tooling even work?

Let's take a good look at the contents of `mcp726a.zip` and see what they do.

# what's in the box

* `conf`: MCP data files, like the `srg`s. I wrote a full document about these in `csvs and srgs.md`.
* `docs`:
  * `README-ECLIPSE.txt` - directions for importing MCP products as Eclipse workspace
  * `README-FF.txt` - Permission to distribute Fernflower (this was pre-jetbrains)
    * `README-LINUX` and `README-OSX` - notes for using mcp on those platforms
  * `README-MCP.txt` - **big** readme file with *lots* of documentation about how to use MCP
  * The source to RetroGuard
  * Fernflower's license
* `eclipse`: eclipse workspace files that are useful after you run MCP
* `jars`: the "work directory", of sorts?
  * You are intended to put the downloaded minecraft jars in here
  * also contains a sample `server.properties` which will be used when launching the integrated server through mcp
* `runtime`
  * **all the Python scripts** used to implement MCP magic
  * more python scripts under `filehandling` and `pylibs`
  * under `bin`:
    * a full Python 2.7 runtime for Windows (lol)
    * `applydiff.exe` (used instead of `patch` on Windows)
    * `astyle.exe` and `astyle-osx`
    * `fernflower.jar`
      * Amusingly it is proguarded
    * **`mcinjector.jar`**
      * "MCInjector v2.8 by Searge, LexManos, Fesh0r"
      * Sometimes called "exceptor" in the scripts
      * All this does is add `throws X` clauses and LVT names 
    * **`retroguard.jar`**
      * Jar remapper? Not sure exactly
* `CHANGELOG`
* `LICENSE` :crayon:
* and a bunch of user-facing scripts:
  * `cleanup`, `decompile`, `getchangedsrc`, `recompile`, `reformat`, `reobfuscate`, `startclient`, `startserver`, `updateids`, `updatemcp`, `updatemd5`, `updatenames`.
  * These are all simply forwarders to the scripts in `pylibs`.
  * Each comes in `.bat` and `.sh` flavors. The batch files use the included Python binaries in `runtime/bin/python`, the shell scripts use the system Python (which is expected to be **Python 2.7**)

This version of MCP expects:

* server jar to exist at `jars/minecraft_server.jar` and to have the md5 `f69ac4bfce2dfbce03fe872518f75a05`
* client jar to exist at `jars/bin/minecraft.jar` and to have the md5 `8e80fb01b321c6b3c7efca397a3eea35`

# usage

`README-MCP.txt` goes over how to create a jarmod using MCP:

0. Install Java 1.6 (or 1.7) and put it on your PATH
1. Copy `minecraft_server.jar` into the `jars` folder
2. Copy the `bin` and `resources` folders from `%APPDATA%/.minecraft` into the `jars` folder
3. Run `decompile.bat`
4. A `src/minecraft` and `src/minecraft_server` folder will be created
5. Have fun jarmodding. Use `recompile.bat` to rebuild and `startclient.bat`/`startserver.bat` to play.
6. When you are done, run `reobfuscate.bat`
7. Collect the classes from `reobf/minecraft` and `reobf/minecraft_server`, package them into a jar, and distribute your jarmod

# how does each script work

Generally:

* `commands.py` contains low-level plumbling tasks
  * also a "library functions" junk-drawer
  * most entries in `conf/mcp.cfg` map to fields on `commands.py`, thanks to `readconf()` (so if you see a magic variable pulled out of thin air, check that file)
  * Lots of the low level business logic (like "how to execute a java program") have their implementations in commands.py
  * It also has an update checker for mcp itself? Lol
* `pylibs/` contains more plumbing tasks, generally more specialized, or taking up enough space to justify splitting them to their own file
* `mcp.py` implements higher-level goals, usually they orchestrate a bunch of tasks from commands.py in sequence (like "do all the decompiling stuff")
* scripts that correspond to a frontend script (like `decompile.bat` -> `decompile.py`) are nicer CLI wrappers for functions in mcp.py or commands.py

## `decompile.py`

* `--client` and `--server` to only do half the work
* `-j` uses JAD even when Fernflower is available (legacy option by this point, fernflower has been in the tool since 7.0a)
* `-s`: "force use of CSVs even if SRGs available", hmm
* `-r`: doesn't recompile after decompiling
* `-d`: doesn't add javadoc comments
* `-a`: skips `astyle`
* `-n`: skips mapping
* `-g`: keeps generics data?
* `-o`: "only patch sources"
* `-p`: "no patch", *"undocumented magic"*
* `-c`: additional configuration file

or in other words

* `use_ff`: pick a decompiler backend: JAD or Fernflower (i will assume Fernflower)
* `use_srg`: pick a class name source: CSVs or SRGs (CSVs are obsolete and not shipped so i will assume SRGs)
* also configure the `no_reformat`, `no_renamer`, `no_patch`, `strip_comments`, and `exc_update` options
* then run `decompile_side` in mcp.py
* if recompilation is requested, also run `updatemd5_side` in mcp.py

### Well Then What does `decompile_side` do

Are You Ready to go down the rabbit hole

Making the above simplifying assumptions, assuming none of the `no_`/`strip_` options are enabled, and with the understanding that this stuff happens again on the server too:

* (earlier, in decompile.py): `creatergcfg`
  * reobf = `False` here.
  * `rgconfig_file`: `temp/retroguard.cfg` (RETROGUARD/RetroConf)
    * (Writes a bunch of options into the file)
  * `rgclientconf_file`: `temp/client_rg.cfg` (RETROGUARD/ClientConf)
    * `input` -> jarclient, `jars/bin/minecraft.jar` (JAR/Client)
    * `output` -> rgclientout, `temp/minecraft_rg.jar` (RETROGUARD/ClientOut)
    * `reobinput` -> cmpjarclient
    * `reoboutput` -> reobfjarclient
    * `script` -> rgconfig (same as `rgconfig_file`)
    * `log` -> rgclientlog, `logs/client_rg.log` (RETROGUARD/ClientLog)
    * `deob` -> srgsclient, `temp/client_rg.cfg` (SRGS/Client)
    * `reob` -> reobsrgclient
    * `nplog` -> rgclientdeoblog, `logs/client_deob.log` (RETROGUARD/ClientDeobLog)
    * `rolog` -> clientreoblog
    * a few more options too, if you can believe it
* Creating SRGs: `createsrgs`
  * client.srg and server.srg simply copied into `temp/client_rg.srg` and `temp/server_rg.srg`
* Applying Retroguard: `applyrg`
  * reobf = `False` here.
  * `rgcplk`: classpath containing mc client, lwjgl, etc
  * `rgconflk`: `temp/client_rg.srg` (same as `rgclientconf_file) (RETROGUARD/ClientConf)
  * `rgdeoblog`: `logs/client_deob.log`
  * `deobsrg`: `temp/client_deobf.srg`
  * `reobsrg`: `temp/client_ro.srg`
  * `rgcmd`: `%s -cp "{classpath}" RetroGuard -searge {conffile}`
  * Format `rgcmd` where %s points to the appropriate `java -jar` command, `{classpath}` is `rgcplk` plus Retroguard, and `conffile` is `rgconflk`.
  * Run the command.
  * Because `reobf = false` here, after running Retroguard, copy `rgdeoblog` to `deobsrg` (and also to `reobsrg`)
    * `reobsrg` will be overwritten way later, in `mcp.updatenames_side`
* Applying MCInjector: `applyexceptor`
  * `excinput`: rgclientout, `temp/minecraft_rg.jar` (RETROGUARD/ClientOut)
  * `excoutput`: xclientout, `temp/minecraft_exc.jar` (EXCEPTOR/XClientOut)
  * `excconf`: xclientconf, `conf/joined.exc` (EXCEPTOR/XClientCfg)
    * nb: the same file used on both sides
  * `exclog`: xclientlog, `logs/client_exc.log` (EXCEPTOR/XClientLog)
  * exceptor command: `%s -jar %s {input} {output} {conf} {log}`, formatted with those previous variables in the same order
  * run the command
* Unpacking jar: `extractjar`
  * `pathbinlk`: binclienttmp, `temp/bin/minecraft` (OUTPUT/BinClientTemp)
  * `jarlk`: xclientout, `temp/minecraft_exc.jar` (EXCEPTOR/XClientOut)
    * written by previous step
  * Unzips the `jarlk` jar into the `pathbinlk` directory.
* Copying classes: `copycls`
  * `pathbinlk`: same as above
  * `pathclslk`: clsclienttmp, `temp/cls/minecraft` (DECOMPILE/ClsClientTemp)
  * `ignore_dirs`: computed from ignorepkg, `paulscode,com/jcraft,isom,ibxm,de/matthiasmann/twl,org/xmlpull,javax/xml` (RECOMPILE/IgnorePkg)
  * Copies *only `.class` files* that do not live in a class starting with one of the `ignore_dirs`, into `pathclslk`.
* (with the decompiler assumption made above) Decompiling: `applyff`
  * `pathclslk`: same as above
  * `pathsrclk`: srcclienttmp, `temp/src/minecraft` (DECOMPILE/SrcClientTemp)
  * Command used: `%s -jar %s -din=0 -rbr=0 -dgs=1 -asc=1 -log=WARN {indir} {outdir}`, indir and outdir formatted using the above variables in the same order
  * names for these options in `IFernflowerPreferences` from modern ff:
    * `din` -> `DECOMPILE_INNER`
    * `rbr` -> `REMOVE_BRIDGE`
    * `dgs` -> `DECOMPILE_GENERIC_SIGNATURES`
    * `asc` -> `ASCII_STRING_CHARACTERS`
* Copying sources: `copysrc`
  * `pathsrctmplk`: same as `pathsrclk` above
  * `pathsrclk`: srcclient, `src/minecraft` (OUTPUT/SrcClient)
  * calls `copyandfixsrc`, which copies only the `.java` files that do not exist in ignorepkg from `pathsrctmplk` into `pathsrclk`, converting to CRLF on Windows and LF on not-Windows
  * on the client only:
    * copies `conf/patches/Start.java` into `pathsrclk`
    * (there's also an option for "fixsound" that is a file copied similarly to fixesclient, but it is obsolete in this MCP version)
* (with the assumption made above) Applying fernflower fixes: `process_fffixes`
  * calls `pylibs/fffix.py` on `pathsrclk` (same as above)
    * Modifies the patched source code using a big glob of regular expressions
    * Some of the stuff is useful: resugaring enums (apparently fernflower couldn't do it at the time), fixing a bizarre ff bug where floats and doubles get decompiled with an extra trailing zero on Mac (???)
    * Other stuff is completely pedantic code-style fixes, like removing blank `super()` calls, trailing whitespace, repeated blank lines, etc
    * Done *before* source-patching, so you'd have to reimplement this to the letter if you wanted to apply source patches w/o the original toolchain. Ah well.
* Applying patches: `applypatches`
  * `pathsrclk`: same as above
  * `patchlk`: ffpatchclient, `conf/patches/minecraft_ff.patch` (PATCHES/FFPatchCLient)
    * this is why JAD is obsolete; it'd use versions without the `_ff` extension, which aren't shipped with MCP anymore
  * preprocesses the patch with `pylibs/normpatch` into patchtemp (PATCHES/PatchTemp)
    * fixes crlf
    * fixes forward-slash backslash issues (patches ship with \ separator)
  * runs the patcher binary with `%s -p1 -u -i {patchfile} -d {srcdir}`
* `mcp.reformat_side`
  * `process_cleanup`
    * `"""Do lots of random cleanups including stripping comments, trailing whitespace and extra blank lines"""` yeah thats accurate
    * implemented in `pylibs/cleanup_src.py`. Fixes things like Fernflower decompiling random constants as `char`s, deletes `import`s that redundantly import from the same package, "unpick"s some various constants
    * all done with textual find-and-replace with regular expressions
  * on the client: Replacing OpenGL constants: `process_annotate`
    * another unpick for opengl constants (uses regex to replace magic numbers in calls to `GL11` methods)
  * if astyle exists: Reformatting sources: `applyastyle`
    * blah blah
* `mcp.updatenames_side`
  * Adding javadoc: `process_javadoc`
    * applies field/method javadoc to the sources using regular expressions
  * **Renaming sources: `process_rename`*
    * reoblk: reobsrgclient, `temp/client_ro.srg` (SRGS/ReobfClient)
    * Read methods.csv, fields.csv, params.csv
    * theres the money shot!!!!!!!! commands.py line 1233!! Names are applied **using find-and-replace on the source code**.
      * things matching `func_[0-9]+_[a-zA-Z_]+` are methods
      * things matching `field_[0-9]+_[a-zA-Z_]+` are fields
      * things matching `p_[\w]+_\d+_` are parameter names
    * Find-replace performed on all `.java` files, as well as on reoblk

### while we're looking at mcp.py, what about `reobfuscate_side`

Its like 4am but this isnt conceptually very difficult

* cleanreobfdir
  * outpathlk: dirreobfclt, `reobf/minecraft` (REOBF/ReobfDirClient)
  * Just deletes that directory
* gathermd5s
  * Writes md5 hash of every class, one per line, into md5 file
  * Not sure why lol, i think partially used for jarmod distribution, so it only releases the classes that are different
* packbin
  * Packs everything into a jar because you can only run retroguard on a jar
  * Might do more stuf
* applyrg
  * With `reobf = true` this time
* unpackreobfclasses
  * Unpacks classes from the retroguarded jar again
  * If the class also exists on the vanilla client, it is not unpacked if it's not different from vanilla

# RetroGuard

RetroGuard is a Java obfuscator and treeshaker by Mark Welsh, started in 1998. (You might be familiar with "ProGuard", which is an unrelated obfuscator [written referencing it around 2002](https://github.com/Guardsquare/proguard/blob/aa1835fb9314c3f6309d099808e803c48de1672f/docs/md/manual/releasenotes.md#version-10-jun-2002).) MCP uses a forked version available [here](https://github.com/ModCoderPack/Retroguard) and in `docs/source/retroguard_src.zip` with many modifications, mostly by Searge and fesh0r. Something to keep in mind is that what RetroGuard refers to as "log files" are machine-parseable and used for more than debugging. The RG site is down but [here's an archive of the documentation.](https://web.archive.org/web/20090708133033/http://www.retrologic.com/retroguard-docs.html). fesh0r removed most of the tree-shaker functionality and other stuff irrelevant to MCP's goals.

MCP RetroGuard is invoked with `-searge {conffile}` for deobfuscating or `-notch {conffile}` for reobfuscating, where `{conffile}` is substituted with `temp/client_rg.cfg` for deobf and `temp/client_ro.cfg` for reobf.

<details><summary>client_rg.cfg</summary>

```
input = jars\bin\minecraft.jar
output = temp\minecraft_rg.jar
reobinput = temp\client_recomp.jar
reoboutput = temp\client_reobf.jar
script = temp\retroguard.cfg
log = logs\client_rg.log
deob = temp\client_rg.srg
reob = temp\client_ro.srg
nplog = logs\client_deob.log
rolog = logs\client_reob.log
verbose = 0
quiet = 1
fullmap = 0
startindex = 0
protectedpackage = paulscode
protectedpackage = com/jcraft
protectedpackage = isom
protectedpackage = ibxm
protectedpackage = de/matthiasmann/twl
protectedpackage = org/xmlpull
protectedpackage = javax/xml
```

</details>

<details><summary>client_ro.cfg</summary>

```
input = jars\bin\minecraft.jar
output = temp\minecraft_rg.jar
reobinput = temp\client_recomp.jar
reoboutput = temp\client_reobf.jar
script = temp\retroguard_ro.cfg
log = logs\client_ro.log
deob = temp\client_rg.srg
reob = temp\client_ro.srg
nplog = logs\client_deob.log
rolog = logs\client_reob.log
verbose = 0
quiet = 1
fullmap = 0
startindex = 0
protectedpackage = paulscode
protectedpackage = com/jcraft
protectedpackage = isom
protectedpackage = ibxm
protectedpackage = de/matthiasmann/twl
protectedpackage = org/xmlpull
protectedpackage = javax/xml
```

</details>

## NameProvider

This class is added by MCP's patches. As a kind of blunt patch, it receives the entire command line arguments, then returns a new list of command line arguments that regular RetroGuard consumes.

There are four NameProvider "modes":

0. `CLASSIC_MODE`: The default mode. assigns unique names to each class, method, method parameter, and field as they're encountered. SRG names actually
   * These days the SRG file is the source of this mapping
1. `CHANGE_NOTHING_MODE`: does not modify names
2. `DEOBFUSCATION_MODE`: proguard -> srg
3. `REOBFUSCATION_MODE`: ??? -> proguard

If you pass `-searge` or `-notch`, execution flows into `parseNameSheetModeArgs`, which sets `NameProvider` to `DEOBFUSCATION_MODE` or `REOBFUSCATION_MODE` respectively. Otherwise `CLASSIC_MODE` is used, and the fifth command line argument is the starting number for its numbering scheme.

`parseNameSheetModeArgs` reads a config file in a custom ini format. Keys read:

* `input`, `output`, `script`, and `log` map to RetroGuard's [first, second, third, and fourth command line arguments](https://web.archive.org/web/20090708090608/http://www.retrologic.com/rg-docs-running.html)
  * `input`: "the original, unobfuscated jar file"
  * `output`: "the filename that will be used for the obfuscated jar file"
  * `script`: "the filename of the RetroGuard script file"
  * `logfile`: "the filename that will be used for the text log of this obfuscation run"
* if `reobinput`/`reoboutput` are set and we're in `REOBFUSCATION_MODE` (`-notch`), they override whatever was set for `input`/`output` (!)
* `deob`, `packages`, `classes`, `methods`, `fields` add the file to the `NameProvider.obfFiles` set
  * Only `deob` is used in practice
* `reob` adds to the `NameProvider.reobFiles` set
* `nplog` sets the `NameProvider.nplog` field (ignored if file doesn't exist)
* `rolog` sets the `NameProvider.rolog` field (ignored if file doesn't exist)
* `startindex` sets `NameProvider.uniqueStart` (same as the handling of the fifth command line argument) (probably unused because that's only used in CLASSIC_MODE)
* `protectedpackage` adds the string to the `NameProvider.protectedPackages` set
* several boolean options: `quiet`, `oldhash`, `fixshadowed` (defaults to true), `incremental` (defaults to true, actually sets `repackage`), `multipass` (defaults to true), `verbose`, `fullmap`
  * setting to something starting with `1`, `t`, or `y` will enable them
  * setting to something starting with `0`, `f`, or `n` will disable them
  * `quiet` and `verbose` change log output
  * `oldhash` switches random RetroGuard data structures to use a `Hashtable` instead of a `HashMap` - probably relevant to `CLASSIC_MODE` ordering
  * normally RetroGuard will look inside parent classes for a field/method with the same name/signature in order to remap it correctly, but will skip doing that if the item is `private` - if `fixShadowed` is set, it will also skip if the item is `static` or `final`
  * `incremental` - not sure
  * `multipass` seems to change the behavior of `NameProvider.retainFromSRG` somehow (maybe making the obfuscation log more complete?)
  * `fullmap` seems to call `setOutput` on a bunch of stuff, maybe making the obfuscation log more complete?

Next, SRGs are read. In deobf mode, we read all SRG files in the `obfFiles` set, and in reobf mode, we read all SRG files in the `reobFiles` set. (only usage of these sets.) Data is entered into `NameProvider.{packages,classes,methods,fields}{Obf2Deobf,Deobf2Obf}` fields.

After that, control flows back into RetroGuard. Various sites throughout RetroGuard have added calls into `NameProvider`.

When a class is written, calls to `NameProvider` have been inserted that dump the used mappings into the `nplog` file (in deobfuscating mode) or `rolog` (in reobfuscating mode). This looks the same as the input SRG file, but due to the encounter order, you get "classes followed by their members" instead of "all the calsses, then all the fields"

Renaming is done by inserting calls to `NameProvider.getNew{TreeItem,Package,Class,Method,Field}Name`. In `CHANGE_NOTHING_MODE` this returns `null`. In `CLASSIC_MODE` a unique name is created. In `DEOBFUSCATION_MODE` the `Obf2Deobf` maps are consulted, and in `REOBFUSCATION_MODE` the `Deobf2Obf` maps are. The only complication is because ProGuard seems to treat package names separably from class names, so there's some toodoo about "class name" vs "full class name" but its not a big deal

```
CL: b net/minecraft/src/CallableMinecraftVersion
FD: b/a net/minecraft/src/CallableMinecraftVersion/field_71494_a
MD: b/a ()Ljava/lang/String; net/minecraft/src/CallableMinecraftVersion/func_71493_a ()Ljava/lang/String;
MD: b/call ()Ljava/lang/Object; net/minecraft/src/CallableMinecraftVersion/call ()Ljava/lang/Object;
CL: c net/minecraft/src/CallableOSInfo
FD: c/a net/minecraft/src/CallableOSInfo/field_71496_a
MD: c/a ()Ljava/lang/String; net/minecraft/src/CallableOSInfo/func_71495_a ()Ljava/lang/String;
MD: c/call ()Ljava/lang/Object; net/minecraft/src/CallableOSInfo/call ()Ljava/lang/Object;
CL: abc net/minecraft/src/ChunkProviderEnd
```

## Deobf/reobf in practice

Deobfing invocation:

* RetroGuard invoked with `-searge`, which
  * (effectively) causes `deob`-related config entries to be read and `reob`-ones to be skipped
  * treats the left column of mappings as "from", and the right column as "to"
* Reads the file mentioned in `deob` setting, `temp/client_rg.srg` (which is a copy of `cfg/client.srg`), as srg mappings
* While remapping, writes to the file mentioned in the `nplog` setting, `logs/client_deob.log`
  * This is effectively a shuffled copy of `client_rg.srg`, but only contains classes that were actually encountered in the jar

Python scripts:

* copy `logs/client_deob.log` into `temp/client_ro.srg` (which is the file the *reobfing* invocation of RetroGuard will invoke)
* uses regex remapping on all the `field_12345_a` and `func_12346_a` entries in the SRG (does this while remapping the source code)

Reobfing invocation:

* RetroGuard invoked with `-notch`, which
  * (effectively) causes `deob`-related config entries to be skipped and `reob`-ones to be read
  * treats the *right* column of mappings as "from", and the *left* column as "to"
  * ignores the config file's `input` and `output` settings in favor of `reobinput` and `reoboutput`
* Reads the file mentioned in `reob` setting, `temp/client_ro.srg` (which is a file the Python scripts stomped on), as srg mappings
* (It also writes a reoblog but it's not used for anything)

# remapping techniques in summary  !

Deobf:

* **RetroGuard** is invoked with `-searge` to go from proguarded class files -> SRG named class files
  * Definitely pays attention to method descriptors
* **Decompilation** goes from SRG named class files -> SRG named java files 
* **Regular expressions** are used to go from SRG named java files -> MCP named java files
  * Does not pay attention to field/method descriptors at all
  * Remaps everything, including string constants (see `Start.java`, which references an srg field name)

Preparing for reobf:

* Deobfing **RetroGuard** invocation outputs the proguarded -> SRG entries that were actually used while deobfing
* **Regular expressions** convert this to a proguarded -> MCP-named mapping set

Reobf:

* **Compilation** goes from MCP-named java files to MCP-named class files
* **RetroGuard** is invoked with `-notch`, reads the prepared remapped mappings *in reverse*, and goes from MCP-named class files to proguarded class files

# (for curiosity) Running original MCP in 2k23 without too much trouble

Probably Windows only (because the weird version of Python is shipped with it), unless you can locate Python ~2.7 for your OS.

The Java version is located in `commands.py` `checkjava()`. This is kind of an ordeal (it checks the Windows registry, `C:/Program Files`, etc in very nonstandard ways, and doesn't use the JAVA_HOME environment variable) so in practice, the command always falls back to `javac`. You know better, so it's not hard to modify the method to hardcode the path:

```python
def checkjava(self):    
    self.cmdjava = '"C:\\Users\\quat\\scoop\\apps\\temurin8-jdk\\current\\bin\\java.exe"'
    self.cmdjavac = '"C:\\Users\\quat\\scoop\\apps\\temurin8-jdk\\current\\bin\\javac.exe"'
    return
```

(In practice, Java 17 seems to work surprisingly well? Recompiling fails, but it can be "fixed" by setting `-source 1.8 -target 1.8` in `conf/mcp.cfg` `CmdRecomp`.) I also recommend setting `verify = True` at the top of `__init__`, which causes more information about the paths to various programs to be printed to `logs/mcp.log`. In general that log file contains a lot of useful information.

I harvested the files MCP wants out of my Gradle cache, these files exist because i've ran `runClient` on a voldeloom 1.4.7 project at least once. If you're not using voldeloom you can probably also find them in your launcher's files, or go dig around on piston-meta. (The DLLs will be distributed contained inside other jars and must be extracted)

* copy `~/.gradle/caches/voldeloom/minecraft-1.4.7-client.jar` to `(mcp root)/jars/bin/minecraft.jar`
* copy `~/.gradle/caches/voldeloom/minecraft-1.4.7-server.jar` to `(mcp root)/jars/minecraft_server.jar`
* copy `~/.gradle/caches/modules-2/files-2.1/net.java.jinput/jinput/2.0.5/39c7 [...]/jinput-2.0.5.jar` to `(mcp root)/jars/bin/jinput.jar`
* copy `~/.gradle/caches/modules-2/files-2.1/org.lwjgl.lwjgl/lwjgl/2.9.0/565d [...]/lwjgl-2.9.0.jar` to `(mcp root)/jars/bin/lwjgl.jar`
* copy `~/.gradle/caches/modules-2/files-2.1/org.lwjgl.lwjgl/lwjgl_util/2.9.0/a778 [...]/lwjgl_util-2.9.0.jar` to `(mcp root)/jars/bin/lwjgl_util.jar`
* (optional, only to *run* the client) copy `.dll`s from `~/.gradle/caches/voldeloom/natives/1.4.7/` to `(mcp root)/jars/bin/natives/`
    * ignore the "jars" folder in there, it doesn't contain the right lwjgl jar for this purpose

Give it time, this version of Fernflower is really slow.