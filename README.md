# Voldeloom-disastertime

Gradle plugin for ~~Fabric~~ ancient versions of Forge. Based on an ancient version of Fabric Loom.

## Sample projects

There doesn't seem to be a nice way to develop a plugin and actually *use* the plugin to see if it works (no, not "write automated tests for the plugin", *actually* use it) at the same time. This is because Gradle is a terrible piece of software.

Apparently the actual recommended way to do this is a) shove everything in `buildSrc` because imagine actually wanting to publish your plugin lol, or b) literally just publishing it to `mavenLocal` any time you do anything, and i guess ??hope the change gets picked up?? when you import the project by-name from an unrelated test project. Both of these are fucking ridiculous, imo.

So: 

* Sample projects contain a line in `settings.gradle` that includes the gradle project as an "included build". Note that this feels a bit backwards because the subfolder is "including" the parent folder. It is what it is.
* In IDEA, you can right-click on each sample project's `build.gradle` and press "Link Gradle Project" towards the bottom of the dropdown. It's like how IntelliJ is able to discover subprojects and put them in the gradle tool window, but it needs a bit of manual assistance cause this isn't a subproject. Then you get gradle IDE integration. Works better than I expect it to, in this obvious nightmare scenario.
* Note that the plugin will be *compiled against* the version of Gradle used in the sample project. I had to blindly rewrite some legacy-handling code to use reflection because the method was removed. Will see what I can do.

## Debugging the plugin

idk lol. Println

## Common problems (for consumers)

*Weird NPEs when only sketching in the build.gradle:* Currently it crashes if a set of mappings is not defined. Ideally it should skip mappings-related tasks instead.

*`Failed to provide null:unspecified:null : conf/packages.csv`:* Mapping parsing/jar remapping/something in that area blew up. If this failed due to missing `packages.csv`, use a mcp zip merged with a forge zip; this is janky as hell I know you shouldn't have to do this. want to fix that.