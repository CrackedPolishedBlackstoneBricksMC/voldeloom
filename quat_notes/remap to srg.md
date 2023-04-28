Its really late but can i figure out how remap-to-srg works. gonna look at Forgegradle 2

* `user` contain some reobf stuff https://github.com/MinecraftForge/ForgeGradle/tree/FG_2.1/src/main/java/net/minecraftforge/gradle/user , interesting is "ReobfMappingType". referring to a file in Constants (SEARGE uses "mcp-srg.srg", NOTCH uses "mcp-notch.srg")

Magic happens in TaskSingleReobf ? looks like the actual remapping tech uses SpecialSource.

ReobfExceptor https://github.com/MinecraftForge/ForgeGradle/blob/FG_2.1/src/main/java/net/minecraftforge/gradle/util/mcp/ReobfExceptor.java might be responsible for writing the reobf srg ? I dunno whether "isDecomp" is set during the reobf task tho hmm (what does reobfexceptor do)

hmmm Looks Like the srg itself is created with https://github.com/MinecraftForge/ForgeGradle/blob/FG_2.1/src/main/java/net/minecraftforge/gradle/tasks/GenSrgs.java GenSrgs task which generates like a million files. i think the important one for this reobf is "mcpToSrg", which contains passthrough class mappings and mcp -> srg field/method mappings

So basically it uses an srgfile that goes directly from named -> intermediary and it uses SpecialSource which i think is a "Smart" remapper that checks the class hiearchy...?

INTERESTING: I think GenSrgs writes srg files that don't include method descriptors at all ?? 