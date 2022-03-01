# NetLogo Extension Plugin

This plugin provides common build boilerplate for NetLogo 6 and 5.3.1 extensions. For versions before 5.3.1, please refer to [version 2.2_5.3](https://github.com/NetLogo/NetLogo-Extension-Plugin/tree/v2.2_5.3-M1)

Currently, the plugin targets **SBT 1.3.13** (use v3.1 for SBT 0.13).

## Usage

For an example usage of this plugin, please see the [Sample Scala Extension](https://github.com/NetLogo/Sample-Scala-Extension)'s [`plugins.sbt`](https://github.com/NetLogo/Sample-Scala-Extension/blob/hexy/project/plugins.sbt) and [`build.sbt`](https://github.com/NetLogo/Sample-Scala-Extension/blob/hexy/build.sbt).

Note especially the use of the language test abilities to run the `tests.txt` file, and the sample models included with
the `packageZip` command with the `netLogoZipExtras` setting.

### Project Files

`project/plugins.sbt`

```scala
resolvers += "netlogo-extension-plugin" at "https://dl.cloudsmith.io/public/netlogo/netlogo-extension-plugin/maven/"

addSbtPlugin("org.nlogo" % "netlogo-extension-plugin" % "5.2.0")
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

By default, the NetLogo Extension Plugin builds the jar files for the project and
copies them and any dependencies into the root of the project repository when you
run the `package` sbt command.  The extension will also copy any files you specify
using the `netLogoPackageExtras` setting in your `build.sbt` file.  This lets the
project be used by NetLogo if it's  copied or symbolically linked to the NetLogo
`extensions` folder.

### Zip Package

The NetLogo Extension Plugin includes a `packageZip` sbt command that will take all the
same fies included when you run the `package` command, along with some extras you can
specify with the `netLogoZipExtras` setting.  The name of the zip file will be
`netLogoExtName-version.zip` with `netLogoExtName` and `version` being pulled from your
`build.sbt` settings file for sbt.  If you have your `version` set appropriately this
should generate a file suitable for use in
[the NetLogo Extensions Library](https://github.com/NetLogo/NetLogo-Libraries).

### Packaging a Zip to Publish

To create a zip file of your extension and all its dependencies and extra files just
run the `packageZip` command.  It will create a `name-version.zip` file in the root of
your folder containing all the files necessary to publish your extension to the NetLogo
extensions library.

### Ignoring the Packaged Files

Because this extension creates extension binary and zip files in the root of your repository
it's a good idea to add them to your `.gitignore` file (if you're using git):

```
*.jar
*.zip
# plus any others you manually add to `netLogoPackageExtras`
```

### Including Extra Files in Build, Packaging, and Tests

You can use the `netLogoPackageExtras` setting to add files to the packaging and testing of your
extension.  The setting is a sequence of tuples, the first value being a file path and the second
being an `Option[String]` to rename the file when it's copied (`None` will use the same file name).
This is especially useful to copy files needed for execution that are not managed by sbt or included
as Java resources, such as external scripts.  These files are also included in the zip file made
with the `packageZip` sbt task.

```scala
netLogoPackageExtras += (baseDirectory.value / "resources" / "include_me_1.txt", None)
```

And if you have items that are just for testing, you can use the `netLogoTestExtras` setting.  Any files
included in the list are copied, and folders are recursively copied, maintaining their directory structure.

```scala
// everything in `test/` directory will be copied to the language test directory when
// the tests are run.
netLogoTestExtras += (baseDirectory.value / "test")
```

And you can include extra files in the `packageZip` sbt task using the `netLogoZipExtras` setting.  This is
useful when you're including extra docs or example models with the zip file.

```scala
// The `README.md` file and everything in `sample_models/` directory will be included in the zip file
// made with the `packageZip` command
netLogoZipExtras ++= Seq(baseDirectory.value / "sample_models", baseDirectory.value / "README.md")
```

### Language Tests

You can easily run headless NetLogo language tests for your extension.  These are described
in [the NetLogo wiki](https://github.com/NetLogo/NetLogo/wiki/Language-tests).  These tests let you write NetLogo code snippets to test your extension without manually running the NetLogo GUI.

In order to setup the language tests, add a class to your tests folder that extends `org.nlogo.headless.TestLanguage`, passing in your test files to its constructor.  Then make sure your test class is referenced in your `build.sbt` file, `Test / scalaSource` for Scala or `Test / javaSource` for Java.  Note that this plugin automatically adds the necessary ScalaTest library to run the tests to your extension project, and sbt will find the ScalaTest test runner when you use the `sbt test` command.

Below is an example from one of the sample packages used to verify this plugin, and [here is an example from the Python extension](https://github.com/NetLogo/Python-Extension/blob/master/src/test/Tests.scala) along with its [`build.sbt` settings](https://github.com/NetLogo/Python-Extension/blob/master/build.sbt).  Setting the `org.nlogo.preferHeadless` property isn't required, but it may help if your extension works with GUI code, like creating menus or dialogs.

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
