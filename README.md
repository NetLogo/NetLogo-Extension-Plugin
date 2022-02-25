# NetLogo Extension Plugin

This plugin provides common build boilerplate for NetLogo 5.3.1 and later extensions. For versions before 5.3.1, please refer to [version 2.2_5.3](https://github.com/NetLogo/NetLogo-Extension-Plugin/tree/v2.2_5.3-M1)

Currently, the plugin targets **SBT 1.3.13** (use v3.1 for SBT 0.13).

## Usage

For an example usage of this plugin, please see the [NetLogo extension activator template](https://github.com/NetLogo/netlogo-extension-activator)'s [`plugins.sbt`](https://github.com/NetLogo/netlogo-extension-activator/blob/master/project/plugins.sbt) and [`build.sbt`](https://github.com/NetLogo/netlogo-extension-activator/blob/master/build.sbt).

### Project Files

`project/plugins.sbt`

```scala
resolvers += "netlogo-extension-plugin" at "https://dl.cloudsmith.io/public/netlogo/netlogo-extension-plugin/maven/"

addSbtPlugin("org.nlogo" % "netlogo-extension-plugin" % "4.0")
```

`build.sbt`

```scala
enablePlugins(org.nlogo.build.NetLogoExtension)

netLogoVersion      := "6.2.2"
netLogoClassManager := "HelloScalaExtension"
netLogoExtName      := "helloscala"
netLogoZipSources   := false
```

### Building to Base Directory

By default, the NetLogo Extension Plugin builds a zip file containing all artifacts.
If you would like it to extract the sbt base directory instead, you can use:

```scala
netLogoTarget := org.nlogo.build.NetLogoExtension.directoryTarget(baseDirectory.value)
```

### Including Extra Files in Packaging and Tests

You can use the `netLogoPackageExtras` setting to add files to the packaging and testing of your
extension.  The setting is a sequence of tuples, the first value being a file path and the second
being an `Option[String]` to rename the file when it's copied (`None` will use the same file name).
Example:

```scala
netLogoPackageExtras += (baseDirectory.value / "resources" / "include_me_1.txt", None)
```

### Language Tests

You can easily run headless NetLogo language tests for your extension.  These are described
in [the NetLogo wiki](https://github.com/NetLogo/NetLogo/wiki/Language-tests).  These tests let you write NetLogo code snippets to test your extension without manually running the NetLogo GUI.

In order to setup the language tests, add a class to your tests folder that extends `org.nlogo.headless.TestLanguage`, passing in your test files to its constructor.  Then make sure your test class is referenced in your `build.sbt` file, `Test / scalaSource` for Scala or `Test / javaSource` for Java.  Note that this plugin automatically adds the necessary ScalaTest library to run the tests to your extension project, and sbt will find the ScalaTest test runner when you use the `sbt test` command.

Below is an example from one of the sample packages used to verify this plugin, and here is an example from the Python extension.  Setting the `org.nlogo.preferHeadless` property isn't required, but it
may help if your extension works with GUI code, like creating menus or dialogs.

```scala
package org.nlogo.extensions.helloscala

import java.io.File
import org.nlogo.headless.TestLanguage

object Tests {
  // file paths are relative to the repository root
  // this example assumes a single `tests.txt` file
  val testFileNames = Seq("tests.txt")
  val testFiles     = testFileNames.map( (f) => (new File(f)).getCanonicalFile )
}

class Tests extends TestLanguage(Tests.testFiles) {
  System.setProperty("org.nlogo.preferHeadless", "true")
}
```

## Building

Simply run the `package` SBT command to build a new version of the plugin `.jar`.  Then, set your SBT project's `plugins.sbt` to reference/fetch the `.jar`.

Run `sbt scripted` to run the sample test projects from `src/sbt-test/netlogo-extension-plugin`.

## Terms of Use

[![CC0](http://i.creativecommons.org/p/zero/1.0/88x31.png)](http://creativecommons.org/publicdomain/zero/1.0/)

The NetLogo Extension plugin is in the public domain.  To the extent possible under law, Uri Wilensky has waived all copyright and related or neighboring rights.

[![Hosted By: Cloudsmith](https://img.shields.io/badge/OSS%20hosting%20by-cloudsmith-blue?logo=cloudsmith&style=flat-square)](https://cloudsmith.com)

Package repository hosting is graciously provided by [Cloudsmith](https://cloudsmith.com).
