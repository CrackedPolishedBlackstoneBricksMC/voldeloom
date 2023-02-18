# how is fernflower interacted with?

see `FernFlowerTask`

* decompiler options:
  * `DECOMPILE_GENERIC_SIGNATURES` (`dgs`) set to `1`
  * `BYTECODE_SOURCE_MAPPING` (`bsm`) set to `1`
  * `REMOVE_SYNTHETIC` (`rsy`) set to `1`
  * `LOG_LEVEL` (`log`) set to `trace`
  * the format fernflower expects is: leading dash on the option, and the key/value separated with an equal sign, `-dgs=1 -bsm=1` etc
* more options:
  * input file (no prefix)
  * `-o=` output file
  * `-l=` linemap file (if one was requested)
  * `-t=` thread count
  * `-m=` mappings file in tinyv2 format
  * for each library: `-e=` and the path to the library

each of these goes in an arraylist

some gradle bullshit to get access to `ProgressLogger` and honestly i don't know if that even works, nice

next, using a javaexec task:

* main class is set to `net.fabricmc.loom.task.fernflower.ForkedFFExecutor` from the plugin
* `-Xms200m` and `-Xmx3G` are set (ok sure)
* `setArgs` is called with the previously computed arguments array
* stderr set to system.error, stdout set to a `ConsumingOutputStream` whos main purpose seems to be updating the progress logger

what is `ForkedFFExecutor`? the javaexec task has it running in a different process

* parses the command line options.
  * three-character long options (fernflower style) are put into an `options` hashmap coercing `true` to `1` and `false` to `0`
  * `-e` is inflated to a real `File` and added to a `libraries` array
  * `-o` and `-l` and `-m` set the output/linemap/mappings files
  * `-t` sets thread count
  * unprefixed argument sets the input file
* after parsing, appends an option `fabric:javadoc` to the options array, not to a string ! but to a live `TinyJavadocProvider` object
* the options map, libraries list, input file, output file, and linemap file are passed to `runFF`
  * *not* the mappings themselves - they are only used for the `TinyJavadocProvider` (!)
* creating a `new Fernflower`, initialize with
  * an `IBytecodeProvider` (it's a copypaste of the one in `ConsoleDecompiler` lol). this seems to plug "stringified file paths" into "bytecode of a class"
  * an `IResultSaver`, using `ThreadSafeResultSaver` which is kinda wacky lookin
  * the options hashmap
  * an `IFernflowerLogger` (loom uses a custom one that logs the current thread id and is really complicated for some reason)
* feed it libraries with `addLibrary` and the input file with `addInput`
* call `decompileContext`
* gucci