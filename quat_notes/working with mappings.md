This is just general notes about "how to work with mappings" because they tend to require somewhat messy data structures; you get a lot of weirdly-structured data and need to query it in various ways.

# todo lol i just found that tiny remapper has an ignoreFieldDescs mode which does plaintext field mapping... yeah

### About tiny-remapper

Looking at this less because it's a great API, and more because it's the API of an off-the-shelf remapper, so it'll drive the decisions we need to make.

There's a visitor pattern (in `IMappingProvider` and `IMappingProvider.MappingAcceptor`) and a tree pattern (in a zillion classes scattered in `net.fabricmc.mapping.tree`, or `net.fabricmc.mappingio.tree` on newer versions). The tree pattern isn't intended to be used for mutation, but supports mapping sets with multiple naming schemes, like the common "official to intermediary to named" pattern.

This is `MappingAcceptor`:

```java
public interface MappingAcceptor {
	void acceptClass(String srcName, String dstName);
	void acceptMethod(Member method, String dstName);
	void acceptMethodArg(Member method, int lvIndex, String dstName);
	void acceptMethodVar(Member method, int lvIndex, int startOpIdx, int asmIndex, String dstName);
	void acceptField(Member field, String dstName);
}
```

where `Member` is a `(String owner, String name, String desc)` that, mildly annoyingly, doesn't define `equals()` or `hashCode()`.

When you use `tiny-remapper` you will provide an `IMappingProvider`, that the remapper will pass its own `MappingAcceptor` to. You must then fill it with mappings. (Loom's `TinyRemapperHelper` contains an example of traversing a `MappingTree` into this `MappingAcceptor`.)

### Class mappings

Old name -> new name, including package information on both. That's all.

Two things, though:

* It's sometimes good to know about inner-class relationships.
* Forge's `packages.csv` assumes all *unqualified* class names are unique (classes can't have the same name even in different packages).

### Method mappings

Methods are uniquely identified by their owning class, name, and descriptor. This isn't too surprising.

For output, you only strictly need to keep track of the remapped method name, because the other two can be computed from the rest of the mappings.

### Field mappings

In tiny-remapper, a field is uniquely identified by its owning class, its name, *and its descriptor*. The descriptor requirement might be surprising because a field's descriptor is its type, and two fields with the same name in the same class are not allowed even if they have different types, right? Well it's not allowed in Java, but it is allowed in Java bytecode, where field accesses are always type-qualified in the bytecode.

You also only need to care about the remapped field name, because the rest can be computed from the rest of the mappings.

### Uniqueness constraints

Requiring field descriptors is kind of annoying when working with MCP, because while Mojang's Proguard settings *did* create clashing method names (see: every class having a bunch of `a` methods), it didn't create those "illegal" clashing field names, so MCP did not bother to save field types in the SRG. To use tiny-remapper, which demands field types, you'll need to glean them off the un-remapped jar first.

Additionally, Searge names (the `func_12345_a` `field_54321_a` stuff) are intentionally all unique, so in these cases the owning class name is not required either. `fields.csv` and `methods.csv` exploit this, omitting the owning class name.

### Summary of access patterms

Classes are indexed by their fully qualified name by both tiny-remapper and by SRGs. (The exception is when reading `packages.csv`, but that can be resolved by removing the package prefix on the fly)

Methods are indexed by tiny-remapper by their owning-class/name/descriptor triple. SRGs provide the entire triple, but `methods.csv` indexes methods by only their name.

Fields are indexed by tiny-remapper by their owning-class/name/descriptor triple. SRGs provide only the owning-class and name, but not the descriptor, and `fields.csv` indexes fields by only their name.