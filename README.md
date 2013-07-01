# NetLogo Extension Plugin

This plugin provides common build boilerplate for NetLogo extensions.  NetLogo extensions that reference NetLogo `master` (or any other 5.1-based branch of NetLogo) should use the `master` branch of this repo.  5.0.x-based NetLogo extensions should use this repo's `5.0.x` branch.

## Building

Simply run the `package` SBT command to build a new version of the plugin `.jar`.  Then, set your SBT project's `plugins.sbt` to reference/fetch the `.jar`.

## Terms of Use

[![CC0](http://i.creativecommons.org/p/zero/1.0/88x31.png)](http://creativecommons.org/publicdomain/zero/1.0/)

The NetLogo Extension plugin is in the public domain.  To the extent possible under law, Uri Wilensky has waived all copyright and related or neighboring rights.
