(See `quat_notes/old notes.md` for stuff that used to be on this page but got outdated)

## Architecture

The entrypoint is `LoomGradlePlugin`, which gets called upon writing the `apply plugin` line.

1. Hello log message is printed
2. `java`, `eclipse`, and `idea` plugins are applied, as if you typed `apply plugin: "eclipse"`
3. `GradleSupport.detectConfigurationNames` determines if you're on a `compile` or `implementation`-flavored version of Gradle
4. An *extension* is created, LoomGradleExtension; this is what defines the `volde {` block you can type some settings into.
    * The settings are not available right away (remember, we're still on the "apply plugin" line when evaluating the script)
    * They will be available in `project.afterEvaluate` blocks, and since tasks are executed after those, in task configuration and execution
5. A couple maven repos are added, as if you typed them in to a `repositories {` block:
    * Mojang's,
    * Minecraft Forge
    * The remapped mod cache, for mod dependencies (project .gradle/voldeloom-cache/remapped_mods)
6. Several [*configurations*](https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.Configuration.html) are created
    * `minecraft` - extends `compile`/`implementation`
        * Minecraft artifact straight off of Mojang's server
    * `minecraftDependencies`
        * Minecraft's own libraries, like LWJGL
    * `forge`
        * Forge artifact straight off of Maven
    * `forgeClient`, `forgeServer`
        * Split Forge artifacts straight off of Maven (for <=1.2.5, which was two separate artifacts)
    * `forgeDependencies`
        * Forge's autodownloaded libraries, like Guava
    * `mappings`
        * MCP mappings artifact. This is either something straight off of Maven (if you're sourcing mappings from a Forge `src` zip) or a file on your computer (if using `volde.layered` mappings)
    * `accessTransformers`
        * Custom Forge-format access transformer files.
    * `minecraftNamed` - extends `minecraftDependencies`, `forgeDependencies`
        * Minecraft named with your chosen mappings
        * `compile`/`implementation` extends from this (so you can code against it)
    * and a couple for mod dependencies. These are added in pairs, and stowed in a "remapped configuration entries" list that later tasks will use
    * `modImplementation` and `modImplementationNamed`
        * `implementation` extends `modImplementationNamed`
        * ends in `compile` instead of `implementation` on Gradle 6-
    * `modCompileOnly` and `modCompileOnlyNamed`
        * `compileOnly` extends `modCompileOnlyNamed`
    * `modRuntimeOnly` and `modRuntimeOnlyNamed`
        * `runtimeOnly` extends `modRuntimeOnlyNamed`
        * no `Only` suffix on Gradle 6-
    * `modLocalRuntime` and `modLocalRuntimeNamed`
        * `runtimeOnly` extends `modLocalRuntimeNamed`
    * Some for coremods, which require special handling:
    * `coremodImplementation` and `coremodImplementationNamed`
        * `compileOnly` extends `coremodImplementationNamed`
    * `coremodRuntimeOnly` and `coremodRuntimeOnlyNamed`
        * no extensions
    * `coremodLocalRuntime` and `coremodLocalRuntimeNamed`
        * no extensions
7. some IntelliJ IDEA settings are configured, same stuff you could do if you wrote an `idea { }` block in the script
8. All the Gradle tasks are registered
    * remapJar
    * genSources
    * genEclipseRuns, genIdeaRuns, genIdeaWorkspace, vscode
    * shimForgeLibraries, shimResources, remappedConfigEntryFolderCopy, printConfigurationsPlease
    * It's set up so that adding a run config to `volde.runs` will add a `runXxx` task for it, then the `client` and `server` run configs are created which adds the runClient and runServer tasks

Then we ask for an `afterEvaluate` callback. The rest of the buildscript in your project runs first, so the callback runs after you have configured the project in the `volde { }` block:

1. Call any "before minecraft setup actions" (callback that the buildscript author can use for any purpose)
2. Call `ProviderGraph#trySetup()`. This is a deeply magical Does-It-All method. Described later.
3. The `jar` and `remapJarForRelease` tasks are wired up:
    * If `remapJarForRelease` doesn't have an input:
        * `jar` is set to a classifier of `"dev"` and `remapJarForRelease` is set to a classifier of `""`.
        * `remapJarForRelease`'s input is set to `jar`'s output.
    * `remapJar`'s output is registered to the `archives` artifact configuration.
    * The Loom gradle extension gets `addUnmappedMod` called on it, set to `jar`'s output (idk)
4. Maven publication settings are configured, to forward mod dependencies into your maven POM.
5. Call any "after minecraft setup actions" (callback that the buildscript author can use for any purpose)

After doing all of that, the task execution phase may begin.

What happens in `ProviderGraph#trySetup`:

1. Ensure there's exactly one dependency in the `minecraft` configuration. Reads its version number.
2. `VanillaJarFetcher`:
    * Download `version_manifest.json`, locate the appropriate per-version manifest.
    * Download the client and server.
3. `VanillaDependencyFetcher`:
    * Examine the per-version manifest for Maven-style dependencies and native libraries.
    * Add the Maven-style dependencies to the Gradle project as regular maven deps.
    * Download the native libraries to a folder.
4. `AssetDownloader`:
    * Just configure it, don't actually download the assets yet.
5. If there's something in the `forge` configuration:
    * `Binpatcher`:
        * Examine the jar for 1.6+-style "binpatches".
        * If they exist, apply binpatches to the client and server jars.
    * `Merger`:
        * Blend the client and server jars into a single `-merged` jar.
    * (Continue from step 7, on the merged jar & merged copy of Forge.)
6. If there's instead something in the `forgeClient` and `forgeServer` jars:
    * (Continue from step 7, on the client jar & client version of Forge.)
    * (Continue from step 7, on the server jar & server version of Forge.)
7. `ForgeDependencyFetcher`:
    * Examine the version of Forge for its additional dependencies.
    * There's various odd formats used over the years, like "automatically-downloaded libraries at runtime" or a `version.json` launcher profile. Handle those.
8. `Jarmodder`:
    * Copy the Forge jar into the vanilla Minecraft jar.
    * Delete `META-INF`.
9. `MappingsWrapper`:
    * Ensure there's exactly one dependency in the `mappings` configuration.
    * Read various MCP `.srg`/`.csv` files and construct `McpMappings` from them.
10. (or step 11, on 1.7+) `AccessTransformer`:
    * Apply Forge's access-transformers to the minecraft jar.
11. (or step 10, on 1.7+) `RemapperMcp`:
    * Apply the `McpMappings` to the jar, creating an SRG-named jar (func_, field_, etc)
12. `NaiveRenamer`:
    * Apply the `fields.csv` and `methods.csv` names from the `McpMappings`.
13. `DependencyRemapperMcp`:
    * Takes mods from the `modImplementation`/etc configurations and remaps them from the release namespace into the workspace names.
14. configure `GenSourcesTask.SourceGenerationJob`s in case you will run `genSources`.
15. Adding the finished Minecraft jar to the `minecraft` configuration:
    * Use the `-linemapped` jar from the last `genSources` execution, if one exists.
    * If not, the output of `NaiveRenamer` is used.
16. Create reobf mappings, that go from the workspace namespace into the release namespace, used by the `remapJarForRelease` task.

## `modImplementation` and friends

`LoomGradleExtension` contains a `NamedDomainObjectController` of `RemappedConfigurationEntry`s. A `RemappedConfigurationEntry` is a pair of configurations:

* an "input config", which the developer is intended to add release-named artifacts into (so, "obfuscated things")
* an "output config", which the `RemappedDependenciesProvider` dep provider will deposit remapped versions of the artifacts inside

and some miscellaneous functionality:

* optionally a "Maven scope" string, which declares how input artifacts get placed on the maven pom
* optionally a "copy to folder" string, which declares a custom folder that output artifacts will be copied into, inside run configs

So, let's say there's an entry with an input config of `modImplementation`, an output of `modImplementationNamed`, and a maven scope of "compile" (which there is, because `LoomGradlePlugin` adds this one by default); and you add `"vazkii:Botania:1.2.3"` to `modImplementation`.

* The dependency remapper will find your `"vazkii:Botania:1.2.3"` dependency (since it's in the input configuration), remap the artifact into the current workspace names, and add the file to the `modImplementationNamed` config (the output config).
    * Because `implementation` was also set to extend from `modImplementationNamed` in `LoomGradlePlugin`, you are able to write code against the mod in your development environment.
    * Because `implementation` extends from `runtimeClasspath` (regular gradle stuff), the mod will appear in your development runClient.
* When you publish to Maven, the POM will mention `"vazkii:Botania:1.2.3"` as a "compile" dependency.

Similar configurations exist under `modCompileOnly` and `modRuntimeOnly` (which map onto the corresponding standard Java Gradle configurations), and `modLocalRuntime` (which is the same as `modRuntimeOnly` but doesn't add to the "runtime" maven scope, intended for simply installing mods into your client workspace)

### and what is the point of the "copy to folder" feature

Forge 1.4.7 has a limitation where it cannot load coremods from the classpath; they *must* exist in the coremods folder only. If you set `copyToFolder("coremods")` on a remapped dependency entry, a Gradle task that runs before any `runXxxx` tasks (`RemappedConfigEntryFolderCopyTask`) will notice, and copy the dependency into the coremods folder for you.

The predefined `coremodImplementation`/`coremodImplementationNamed` entry, for example, only sets `coremodImplementationNamed` to extend from `compileOnly`, not `implementation`. This means it doesn't get put on the runtime classpath the usual way (by way of `runtimeClasspath` extending `implementation`). Forge picks up on the mod because the jar has been copied into the `coremods` folder, though.

There are `coremodImplementation`, `coremodRuntimeOnly`, and `coremodLocalRuntime` configurations predefined. `coremodCompileOnly` does *not* exist because the folder-copy workaround is only required to load the coremod *in the local development workspace*; if it existed, it would be identical to `modCompileOnly`, so just use that.