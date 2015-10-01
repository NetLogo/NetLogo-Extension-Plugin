enablePlugins(org.nlogo.build.NetLogoExtension)

scalaVersion := "2.11.7"

scalaSource in Compile := { baseDirectory.value  / "src" }

javaSource in Compile  := { baseDirectory.value / "src" }

scalacOptions ++= Seq("-deprecation", "-unchecked", "-Xlint", "-Xfatal-warnings",
                        "-encoding", "us-ascii")

javacOptions ++= Seq("-g", "-deprecation", "-Xlint:all", "-encoding", "us-ascii")

name := "Hello-Extension"

libraryDependencies +=
  "org.nlogo" % "NetLogo" % "5.3-LevelSpace" from "http://ccl.northwestern.edu/devel/NetLogo-5.3-LevelSpace-3a6b9b4.jar"

netLogoClassManager := "HelloScalaExtension"

netLogoExtName      := "helloscala"

netLogoZipSources   := false
