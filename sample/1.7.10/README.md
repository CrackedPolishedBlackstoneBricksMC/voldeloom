# Notice

due to an error on the Forge maven (the Forge 1.7.10 `-src` artifact is actually the mod development kit) we must manually fetch Forge's sources to get at its MCP config.

You can do this with the shell script `./create-hilarious-forge.sh`. This needs `git` and `zip`. All it does is run a `git clone` on `github.com/MinecraftForge/MinecraftForge`, checks out a commit, then deletes the git metadata and wraps it up into a zip file at `./work/hilarious-funny-forge.zip`. The build.gradle depends on this file. (There is no version of this shell script for Windows. You can get pretty far executing the script with "git bash", although that software package doesn't include `zip` so the compression step will fail. Just do it yourself with windows explorer or something. You can also execute the script with WSL, but Git under WSL is very slow.)

Inside the zip, there should be a path `/forge/fml/conf/` that contains MCP data - that's where the plugin expects to find them when it reads a jar. It will also read them from `/fml/conf`, `/conf`, and the root, though, so you should be okay lol