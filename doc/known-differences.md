# Differences between this toolchain and period-accurate Forge

Basically this uses a more Fabricy "do as much as possible with binaries" approach. This partially owes to the project's roots in Fabric Loom, which is a completely binary-based modding toolchain, but also because it's a good idea.

* Operating at the level of whole class files, we install Forge the end-user way by downloading Minecraft, pasting the release Forge jar on top, and deleting META-INF.
* Operating inside each class file, we then apply dev-environment creature-comforts like statically applied access transformers, remapping to MCP, etc.
* Only *then* do we even *think* about touching Fernflower. It's even optional; running `genSources` is not required to compile a mod.

This means Voldeoom is *very* fast, at the cost of "missing comments inside Forge source patches". These days we have a much more well-rounded set of class binary-manipulation tools available straight off-the-shelf, like `tiny-remapper`, `JarMerger`, Java's `ZipFileSystem`, etc, that make working with class binaries expressive and fun.

Note: We install Forge on top of a merged client+server jar, when the period-accurate installation process would probably paste Forge on top of merely a client jar or server jar. This ends up seamless because Forge's class-overwrites and binpatches were evidently computed against a merged jar in the first place; they patch in `@SideOnly` annotations, for example.

This also means there are remapping edge-cases:

* [Some mods are impossible to remap out of Proguarded names unambiguously](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom/issues/17); MCP ignored the problem (it never remaps mods out of Proguarded names!) but tiny-remapper notices
* [Whatever this is](https://github.com/unascribed/BuildCraft/commit/06dc8a89f4ea503eb7dc696395187344658cf9c1)