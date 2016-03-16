# NetLogo Extension Plugin

This plugin provides common build boilerplate for NetLogo 5.3.1 and later extensions. For versions before 5.3.1, please refer to [version 2.2_5.3](https://github.com/NetLogo/NetLogo-Extension-Plugin/tree/v2.2_5.3-M1)

Currently, the plugin targets **SBT 0.13.**

## Building

Simply run the `package` SBT command to build a new version of the plugin `.jar`.  Then, set your SBT project's `plugins.sbt` to reference/fetch the `.jar`.

## Usage

For an example usage of this plugin, please see the [NetLogo extension activator template](https://github.com/NetLogo/netlogo-extension-activator)'s [`plugins.sbt`](https://github.com/NetLogo/netlogo-extension-activator/blob/master/project/plugins.sbt) and [`build.sbt`](https://github.com/NetLogo/netlogo-extension-activator/blob/master/build.sbt).

### Project Files

`project/plugins.sbt`

```scala
resolvers += Resolver.url(
  "NetLogo-JVM",
    url("http://dl.bintray.com/content/netlogo/NetLogo-JVM"))(
        Resolver.ivyStylePatterns)

addSbtPlugin("org.nlogo" % "netlogo-extension-plugin" % "3.0-M1")
```

`build.sbt`

```scala
enablePlugins(org.nlogo.build.NetLogoExtension)

netLogoVersion      := "5.3.1"

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

## Terms of Use

[![CC0](http://i.creativecommons.org/p/zero/1.0/88x31.png)](http://creativecommons.org/publicdomain/zero/1.0/)

The NetLogo Extension plugin is in the public domain.  To the extent possible under law, Uri Wilensky has waived all copyright and related or neighboring rights.
