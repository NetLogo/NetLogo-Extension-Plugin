enablePlugins(SbtPlugin)

sbtPlugin      := true
scalaVersion   := "2.12.17"
scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings", "-Xlint", "-release", "17")

name         := "netlogo-extension-plugin"
organization := "org.nlogo"
licenses     += ("Creative Commons Zero v1.0 Universal Public Domain Dedication", url("https://creativecommons.org/publicdomain/zero/1.0/"))
version      := "7.0.0"
isSnapshot   := true

publishTo := { Some("Cloudsmith API" at "https://maven.cloudsmith.io/netlogo/netlogo-extension-plugin/") }

scriptedLaunchOpts := { scriptedLaunchOpts.value ++
  Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
}

scriptedBufferLog := false
