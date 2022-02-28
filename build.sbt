enablePlugins(ScriptedPlugin)

sbtPlugin := true

scalaVersion := "2.12.3"

organization := "org.nlogo"

name := "netlogo-extension-plugin"

version := "5.1"

isSnapshot := true

licenses += ("Creative Commons Zero v1.0 Universal Public Domain Dedication", url("https://creativecommons.org/publicdomain/zero/1.0/"))

publishTo := { Some("Cloudsmith API" at "https://maven.cloudsmith.io/netlogo/netlogo-extension-plugin/") }

scriptedLaunchOpts := {
  scriptedLaunchOpts.value ++ Seq("-Xmx1024M", s"-Dplugin.version=${version.value}")
}

scriptedBufferLog := false
