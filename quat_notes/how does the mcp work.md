# how does period-accurace MCP tooling even work?

Let's take a good look at the contents of `mcp726a.zip` and see what they do.

## what's in the box

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

## usage

`README-MCP.txt` goes over how to create a jarmod using MCP:

0. Install Java 1.6 (or 1.7) and put it on your PATH
1. Copy `minecraft_server.jar` into the `jars` folder
2. Copy the `bin` and `resources` folders from `%APPDATA%/.minecraft` into the `jars` folder
3. Run `decompile.bat`
4. A `src/minecraft` and `src/minecraft_server` folder will be created
5. Have fun jarmodding. Use `startclient.bat` and `startserver.bat` to play.
6. When you are done, run `reobfuscate.bat`
7. Collect the classes from `reobf/minecraft` and `reobf/minecraft_server`, package them into a jar, and distribute your jarmod

## how does each script work

### `commands.py`

* Library used by many of the commands
* most entries in `conf/mcp.cfg` map to fields on `commands.py`, thanks to `readconf` (so if you see some magic variable pulled out of thin air, check that file)
* Lots of the low level business logic (like "executing a java program") have their implementations in commands.py, with individual scripts mainly being the CLI frontend
* It also has an update checker for mcp itself

### `mcp.py`

* Driver

### `decompile.py`

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

#### Well Then What does `decompile_side` do

Are You Ready to go down the rabbit hole

Making the above simplifying assumptions, and also assuming none of the `no_`/`strip_` options are enabled:

* (some time before this, not sure when): `creatergcfg`
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
  * Because `reobf = false` here, after running Retroguard, copy `rgdeoblog` to `deobsrg` and to `reobsrg`
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

#### while we're looking at mcp.py, what about `reobfuscate_side`

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