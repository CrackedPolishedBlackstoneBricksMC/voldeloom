1.6.4 FML still used Ant https://github.com/MinecraftForge/FML/blob/1.6.4/build.xml and cleaned both `argo` and `org`

blame for `cleanargo`:

* [Merge in binpatch and use launcher](https://github.com/MinecraftForge/FML/commit/2eb81ac88bc4142811eb725f0bff269a189d827f)  just moved it - Jun 14 2013
* [Change to download bouncy castle as well](https://github.com/MinecraftForge/FML/commit/a513060a81ac4b245b4f19b5ac3e589eb15e3515) added `org` - Dec 11, 2012
* [Tweak build xml for more cleanliness](https://github.com/MinecraftForge/FML/commit/dfc6f7b1c1bbea0fb276217ccbe56bfa415c7fda) added the `cleanargo` task separately from another task (Jul 3, 2012)
* [Update some stuff](https://github.com/MinecraftForge/FML/commit/982271e8ecaaa20519739054ff85968b60cb6bfe) added the argo deletion (Jul 2, 2012)

for context (because it's hard to find what version of minecraft FML is for in the repo, lol, and there's lots of missing tags)

## timeline!

* 1.2.1 snapshots begin - Jan 19, 2012
* 1.2.1 - Mar 1, 2012
* first Forge 1.2.x release (for 1.2.3) - Mar 5, 2012
* 1.2.5 snapshots begin - Mar 30, 2012
* 1.2.5 - Apr 4, 2012
* 1.3.1 snapshots begin - Apr 12, 2012
* 12w18a, the client-server merging snapshot - May 3, 2012
* commit "Update some stuff" (beginning of argo deletion) - Jul 2, 2012
* commit "Tweak build xml for more cleanliness" - Jul 3, 2012
* 1.3.1 - Aug 1, 2012
* last Forge 1.2.5 release - Aug 3, 2012 ("final commit for 1.2.5", most commits done before Aug 1 tho)
* first Forge 1.3.2 release - Aug 11, 2012
  * yes, forge for 1.3.2 came out before 1.3.2, according to the forge maven/minecraft wiki dates
  * this is just an error on whoever categorized the minecraft versions on files.minecraftforge.net lol, the first versions were for 1.3.1
  * i imagine the first not completely ass broken version was for 1.3.2 though, it took them a while to find footing
* 1.3.2 snapshots begin - Aug 14, 2012
* 1.3.2 - Aug 16, 2012
* last Forge 1.3.2 release - Oct 19, 2012
* commit "Change to download bouncy castle as well" (beginning of org deletion) - Dec 11, 2012

I guess the open question is whether `Update some stuff` made it into only 1.3 or if it also went into 1.2. regardless, i can be pretty confident that bouncycastle was not deleted until 1.4.

## forge 4.0.0.182 (last pre-merge version)

README-fml says the FML version is `3.0.24.253`.

mcp version `7.0a` for 1.3.1

## forge 4.0.0.183 (first post-merge version)

README-fml says the FML version is `3.0.42.270`

mcp version `7.0a` for 1.3.1 (same one)