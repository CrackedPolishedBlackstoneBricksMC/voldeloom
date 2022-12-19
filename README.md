# Voldeloom-disastertime

Gradle plugin for ~~Fabric~~ ancient versions of Forge. Based on an ancient version of Fabric Loom.

## Sample projects

There doesn't seem to be a nice way to develop a plugin and actually *use* the plugin to see if it works (no, not "write automated tests for the plugin", *actually* use it) at the same time. This is because Gradle is a terrible piece of software.

Apparently the actual recommended way to do this is a) shove everything in `buildSrc` because imagine actually wanting to publish your plugin lol, or b) literally just publishing it to `mavenLocal` any time you do anything, and i guess hope the change gets picked up in an unrelated gradle project somewhere. Both of these are fucking ridiculous.

Sample projects contain a line in `settings.gradle` that includes the gradle project as an "included build". This feels a bit backwards because the subfolder is "including" the parent folder but it is what it is.