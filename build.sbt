sbtPlugin := true

scalaVersion := "2.12.2"

organization := "org.nlogo"

name := "netlogo-extension-plugin"

version := "3.2"

isSnapshot := false

licenses += ("Creative Commons Zero v1.0 Universal Public Domain Dedication", url("https://creativecommons.org/publicdomain/zero/1.0/"))

publishTo := { Some("Cloudsmith API" at "https://maven.cloudsmith.io/netlogo/netlogo-extension-plugin/") }
