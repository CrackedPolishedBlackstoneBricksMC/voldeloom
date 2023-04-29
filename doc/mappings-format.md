This document might be of interest to people developing tools that export mappings for Voldeloom to ingest. The file Voldeloom expects you to put in the `mappings` configuration is a zip archive containing the following files somewhere inside:

* `joined.srg`, `client.srg`, `server.srg` - Class name proguard -> named mappings, and field/method proguard -> intermediary name mappings.
  * If `joined.srg` does not exist, `joined.csrg` is checked next. 
  * (Information from `client` and `server` is used on <=1.2.5 split Minecrafts, `joined` used in all other circumstances.)
* `packages.csv` - A packaging transformation file applied to `joined.srg`.
* `fields.csv` - Field intermediary -> named mappings and comments.
* `methods.csv` - Method intermediary -> named mappings and comments.

Files may be located in any number of subdirectories. If more than one of these files exists in the zip archive, *even in different directories*, the behavior is undefined.

(Voldeloom does not read files under the name `client.csrg`, `server.csrg`, or any sort of `packages.csv` transformation for client/server split srgs. Write me if you're interested in this, it's not hard to add and the only reason it doesn't is because Forge never used those types of files.)

Class names are always presented in "internal format"; `java/lang/Math` instead of `java.lang.Math`.

# SRG format

Class `net.fabricmc.loom.mcp.Srg`.

Voldeloom parses a hybrid `.srg` and `.csrg` file format (regardless of the file extension). Parses line-by-line, skipping empty lines, `split`ting on spaces:

* If `split[0]` is "PK:", skip this line.
* If `split[0]` is "CL:", adds a class mapping from `split[1]` to `split[2]`.
* If `split[0]` is "FD:", adds a field mapping:
  * `split[1]` is the field's unmapped owning class, then a `/` character, then the field's unmapped name
  * `split[2]` is the field's mapped owning class, then a `/` character, then the field's intermediary name
* If `split[0]` is "MD:", adds a method mapping:
  * `split[1]` is the method's unmapped owning class, then a `/` character, then the method's unmapped name
  * `split[2]` is the method's unmapped descriptor
  * `split[3]` is the method's mapped owning class, then a `/` character, then the method's intermediary name
  * `split[4]` is the method's mapped descriptor
* Otherwise, if the line consists of two elements separated by spaces and `split[0]` doesn't end in a `/` character, adds a class mapping from `split[0]` to `split[1]`.
* Otherwise, if the line consists of three elements separated by spaces, adds a field mapping:
  * `split[0]` is the field's unmapped owning class
  * `split[1]` is the field's unmapped name
  * `split[2]` is the field's intermediary name
* Otherwise, if the line consists of four elements separated by spaces, adds a method mapping:
  * `split[0]` is the method's unmapped owning class
  * `split[1]` is the method's unmapped name
  * `split[2]` is the method's unmapped descriptor
  * `split[3]` is the method's intermediary name
  * The method's mapped descriptor is computed by remapping the descriptor of `split[2]` using all class mappings *seen so far.*
* Otherwise, report a syntax error.

Voldeloom will always *write* "old style" `.srg` files using the prefixed format. Voldeloom does not write `PK:` lines.

# `packages.csv`

Class `net.fabricmc.loom.mcp.Packages`.

Parses line-by-line, skipping empty lines, splitting on commas.

* If the first line is `class,package`, skip it.
* Otherwise, `split[0]` is the *simple* name of a class to repackage, and `split[1]` is the package to put it in.

When Voldeloom reads mappings, it effectively modifies `joined.srg` according to this packaging transformation. It does not keep track of packages otherwise. Voldeloom will not write out a `packages.csv` file.

# `fields.csv`/`methods.csv`

Class `net.fabricmc.loom.mcp.Members`.

Parses line-by-line, skipping empty lines, splitting on the first three commas.

* If the first line is `searge,name,side,desc`, skip it.
* Otherwise:
  * `split[0]` is the intermediary name of a field (no owning class information is present)
  * `split[1]` is the named name of the field
  * `split[2]` must parse as an integer, but is otherwise (currently) ignored
  * `split[3]`, if present and nonempty, is the field/method's comment
    * if it starts and ends with `"` characters they are stripped