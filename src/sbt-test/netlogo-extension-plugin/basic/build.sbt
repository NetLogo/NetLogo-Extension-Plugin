enablePlugins(org.nlogo.build.NetLogoExtension)

scalaVersion := "2.12.3"

(Compile / scalaSource) := { baseDirectory.value / "src" }
(Compile / javaSource)  := { baseDirectory.value / "src" }

scalacOptions ++= Seq("-deprecation", "-unchecked", "-Xlint", "-Xfatal-warnings", "-encoding", "us-ascii")
javacOptions  ++= Seq("-g", "-deprecation", "-Xlint:all", "-encoding", "us-ascii")

name := "Hello-Extension"

netLogoVersion      := "6.2.2"
netLogoClassManager := "HelloScalaExtension"
netLogoExtName      := "helloscala"
