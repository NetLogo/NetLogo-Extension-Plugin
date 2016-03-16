import org.nlogo.build.NetLogoExtension

enablePlugins(NetLogoExtension)

scalaVersion := "2.11.7"

scalaSource in Compile := { baseDirectory.value  / "src" }

javaSource in Compile  := { baseDirectory.value / "src" }

scalacOptions ++= Seq("-deprecation", "-unchecked", "-Xlint", "-Xfatal-warnings",
                        "-encoding", "us-ascii")

javacOptions ++= Seq("-g", "-deprecation", "-Xlint:all", "-encoding", "us-ascii")

name := "Hello-Extension"

libraryDependencies ++= Seq("commons-lang" % "commons-lang" % "2.6")

netLogoVersion      := "6.0-M1"

netLogoClassManager := "HelloScalaExtension"

netLogoExtName      := "helloscala"

netLogoZipSources   := false

netLogoTarget       := NetLogoExtension.directoryTarget(baseDirectory.value)
