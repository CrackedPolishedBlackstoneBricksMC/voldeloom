ass disassembly done with the ASM Bytecode Viewer intellij extension, having the `minecraft-1.4.7-forge-1.4.7-6.6.2.534-merged.jar` open and browsing through the official-named file

# methods that fail to decompile at all

## `multiplyBy32AndRound (D)I` -> `a`

in `net.minecraft.entity.EnumEntitySize` -> `lt`

switch over `net.minecraft.entity.EnumEntitySize` -> `lt`

switchmap misplaced to `net.minecraft.entity.EnumEntitySizeHelper` `field_85153_a` -> `ls`

disassembly:

```
   L1
    LINENUMBER 120 L1
    GETSTATIC ls.a : [I
    ALOAD 0
    INVOKEVIRTUAL lt.ordinal ()I
    IALOAD
    TABLESWITCH
```

## `placeDoor (Lnet/minecraft/world/World;Ljava/util/Random;Lnet/minecraft/world/gen/structure/StructureBoundingBox;Lnet/minecraft/world/gen/structure/EnumDoor;III)V` -> `a`

in `net.minecraft.world.gen.structure.ComponentStronghold` -> `aet`

switch over `net.minecraft.world.gen.structure.EnumDoor` -> `aeu`

switchmap misplaced to `net.minecraft.world.gen.structure.EnumDoorHelper` `doorEnum` -> `aed`

disassembly:

```
   L0
    LINENUMBER 214 L0
    GETSTATIC aed.a : [I
    ALOAD 4
    INVOKEVIRTUAL aeu.ordinal ()I
    IALOAD
    TABLESWITCH
```

## `getAppDir (Ljava/lang/String;)Ljava/io/File;` -> `a`

in `net.minecraft.client.Minecraft` -> `net/minecraft/client/Minecraft`

switch over `net.minecraft.util.EnumOS` -> `aso`

switchmap misplaced to `net.minecraft.client.EnumOSHelper` `field_90049_a` -> `ase`

disassembly:

```
   L1
    LINENUMBER 568 L1
    GETSTATIC ase.a : [I
    INVOKESTATIC net/minecraft/client/Minecraft.c ()Laso;
    INVOKEVIRTUAL aso.ordinal ()I
    IALOAD
    TABLESWITCH
```

## `getOptionOrdinalValue (Lnet/minecraft/client/settings/EnumOptions;)Z` -> `b`

in `net.minecraft.client.settings.GameSettings` -> `ast`

switch over `net.minecraft.client.settings.EnumOptions` -> `asv`

switchmap misplaced to `net.minecraft.client.settings.EnumOptionsHelper` `enumOptionsMappingHelperArray` -> `asu`

disassembly:

```
   L0
    LINENUMBER 379 L0
    GETSTATIC asu.a : [I
    ALOAD 1
    INVOKEVIRTUAL asv.ordinal ()I
    IALOAD
    TABLESWITCH
```

# methods that contain switchmap gunk but decompiled okay

## `getArmorCraftingMaterial` -> `b`

in `net.minecraft.item.EnumArmorMaterial` -> `sv`

switch over `net.minecraft.item.EnumArmorMaterial` -> `sv`

intellij fernflower puts the switchmap at `1.$SwitchMap$net$minecraft$item$EnumArmorMaterial`

MCP has a mapping from `net.minecraft.src.EnumArmorMaterial$1` to `sv$1`

disassembly (this comes from the release Forge jar):

```
   L0
    LINENUMBER 67 L0
    GETSTATIC sv$1.$SwitchMap$net$minecraft$item$EnumArmorMaterial : [I
    ALOAD 0
    INVOKEVIRTUAL sv.ordinal ()I
    IALOAD
    TABLESWITCH
```

## `canSustainPlant` -> (added by forge patch)

in `net.minecraft.block.Block` -> `amq`

switch over `net.minecraftforge.common.EnumPlantType` -> (added by forge patch)

intellij fernflower puts the switchmap at `1.$SwitchMap$net$minecraftforge$common$EnumPlantType`

No relevant MCP mapping

disassembly (this comes from the release Forge jar):

```
   L7
    LINENUMBER 2128 L7
   FRAME SAME
    GETSTATIC amq$1.$SwitchMap$net$minecraftforge$common$EnumPlantType : [I
    ALOAD 8
    INVOKEVIRTUAL net/minecraftforge/common/EnumPlantType.ordinal ()I
    IALOAD
    TABLESWITCH
```

## `getToolCraftingMaterial` -> `f`

in `net.minecraft.item.EnumToolMaterial` -> `uq`

switch over `net.minecraft.item.EnumToolMaterial` -> `uq`

intellij fernflower puts the switchmap at `1.$SwitchMap$net$minecraft$item$EnumToolMaterial`

MCP has a mapping from `net.minecraft.src.EnumToolMaterial$1` -> `uq$1`

```
   L0
    LINENUMBER 92 L0
    GETSTATIC uq$1.$SwitchMap$net$minecraft$item$EnumToolMaterial : [I
    ALOAD 0
    INVOKEVIRTUAL uq.ordinal ()I
    IALOAD
    TABLESWITCH
```

# Conclusions

All of the decompiling-fine switches were added in Forge patches. I can't get in-game because of the Block switch crashing, but at least the `getOptionOrdinalValue` and `getAppDir` methods execute correctly.

Patching Forge (in Voldeloom) entails taking files out of the Forge jar and copy-pasting them into the Minecraft jar, oldschool delete-meta-inf style. The compiled classes in the jar make *reference* to their switchmap classes (like `Block`(`amq`) has an `INNERCLASS` attribute for `amq$1`) but I.... don't think they were actually shipped in the jar?

# Click here to die instantly

Nope, they were shipped, IntelliJ (maybe the Archive Browser extension idk) just fucking lies lol. They show up in 7zip but not intellij. Cool awesome

So. They must be getting lost along the way when remapping

To actually use javap on a jar file in powershell, `javap -s -p -c -verbose jar:file:///$($PWD -replace '\\', '/')/your-cool-jar.jar!/path/to/class.class`