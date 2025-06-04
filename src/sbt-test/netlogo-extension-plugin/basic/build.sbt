enablePlugins(org.nlogo.build.NetLogoExtension)

scalaVersion := "2.12.17"

(Compile / scalaSource) := { baseDirectory.value / "src" }
(Compile / javaSource)  := { baseDirectory.value / "src" }

scalacOptions ++= Seq("-deprecation", "-unchecked", "-Xlint", "-Xfatal-warnings", "-encoding", "us-ascii")
javacOptions  ++= Seq("-g", "-deprecation", "-Xlint:all", "-encoding", "us-ascii")

name := "Hello-Extension"

netLogoVersion      := "7.0.0-beta1"
netLogoClassManager := "HelloScalaExtension"
netLogoExtName      := "helloscala"
