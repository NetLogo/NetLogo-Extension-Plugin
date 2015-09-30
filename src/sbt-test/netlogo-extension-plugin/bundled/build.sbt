import org.nlogo.build.NetLogoExtension

enablePlugins(NetLogoExtension)

scalaVersion := "2.11.7"

scalaSource in Compile := { baseDirectory.value  / "src" }

javaSource in Compile  := { baseDirectory.value / "src" }

scalacOptions ++= Seq("-deprecation", "-unchecked", "-Xlint", "-Xfatal-warnings",
                        "-encoding", "us-ascii")

javacOptions ++= Seq("-g", "-deprecation", "-Xlint:all", "-encoding", "us-ascii")

name := "Hello-Extension"

libraryDependencies ++= Seq(
  "org.nlogo" % "NetLogo" % "5.3-LevelSpace" from "http://ccl.northwestern.edu/devel/NetLogo-5.3-LevelSpace-3a6b9b4.jar",
  "commons-lang" % "commons-lang" % "2.6")

netLogoClassManager := "HelloScalaExtension"

netLogoExtName      := "helloscala"

netLogoZipSources   := false

netLogoTarget       := NetLogoExtension.directoryTarget(baseDirectory.value)
