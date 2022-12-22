# voldeloom-disastertime 1.4.7 sample project

things to note:

* gradle 7.6 is used. how modern!
* **`settings.gradle` contains a dumb hack to be able to include the parent project as a gradle plugin**
  * this hack also causes the plugin to be *compiled* against gradle 7.6's api
* buildscript contains some silliness related to applying forge's access transformers and whatever onto regular mcp. i'll have to see what i can do
* the `toolchains` feature is used to compile to java 6-compatible bytecode (required to make Forge's asm4 happy)