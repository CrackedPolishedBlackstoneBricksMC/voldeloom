Welcome to the messy, ad-hoc documentation zone for Voldeloom. Please pardon my dust.

## Usage

A perpetual work in progress as the plugin is in flux, but see [the main readme](../README.md) and [./samples.md](./samples.md) for how to start building off the sample projects.

Configuration guides:

* [./volde.md](./volde.md) (for the top-level `volde` block)
* [./forge-capabilities.md](./forge-capabilities.md) (for the `forgeCapabilities` block inside)
* [./genSources.md](./genSources.md) (for the configuration knobs exposed on the `genSources` task)

## What works and what doesn't work

[./what-works.md](./what-works.md), [./known-differences.md](./known-differences.md).

## Troubleshooting

[./troubleshooting.md](./troubleshooting.md)

## For curious people

[./arch.md](./arch.md): Architecture of Voldeloom. Also a good document for untangling what `modImplementationNamed` and friends are for.

[./mappings-format.md](./mappings-format.md): The mappings format Voldeloom expects.

Extra curious people may enjoy [../quat_notes](../quat_notes/README.md) which was my notebook during development, and [this page](https://notes.highlysuspect.agency/voldeloom-stages.html) on my garden.

## Doc wishlist

* Actual honest-to-goodness usage guide
* Everything you can do to run configs (in the mean time, read `RunConfig.java` and `task/RunTask.java`)
* Talk about the layered mappings system since it's pretty cool! You can use it to duct-tape-fix problems with mappings ([e.g.](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom/issues/17#issuecomment-2888004494)). The API is a bit messy.