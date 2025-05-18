# Running sample projects

There doesn't seem to be a nice way to develop a Gradle plugin and actually *use* the plugin to see if it works at the same time. This is because Gradle sucks. Therefore, the sample projects contain a line in `settings.gradle` that includes the main voldeloom project as an "included build". This feels backwards because the subfolder is "including" the parent folder. C'est la vie.

Gotchas with this scheme:

* The "root project" is actually the sample project, so run configs generate in the wrong spot. Basically you need to make a `./sample/1.4.7/.idea` directory, voldeloom will think it belongs to the root project and dump run configs into that, copypaste them back into `./.idea`, restart IDE. There's your run configs. (Or use `runClient`.)
* Because of the included-build mechanism, the sample project is actually in charge of *compiling* the Gradle plugin too. So on Gradle 4 it will be compiled against the Gradle 4 api and on Gradle 7 it will be compiled against the Gradle 7 api. Therefore Voldeloom needs to use reflection to access anything not in the common intersection of Gradle 4 and 7.

IntelliJ users can right-click on each sample project's `build.gradle` and press "Link Gradle Project" towards the bottom of the dropdown. The sample projects will then appear in the Gradle tool window for perusal. (It seems like code-completion in the editor uses the Gradle API that you last refreshed a project from.)

Do **not** press the "reload all gradle projects" buttons. Because of the multiple Gradle versions in play they will fail to read each other's lock files and corrupt each other's caches. To refresh projects, right-click on each sample project in the tool window you're interested in and refresh it individually.

Breakpoints don't work if you just hit the "refresh gradle" button, but if you select the task in the `Select Run/Debug Configuration` bar, you can press the debug button.
