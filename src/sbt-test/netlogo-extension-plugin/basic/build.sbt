import org.nlogo.build.NetLogoExtension

enablePlugins(NetLogoExtension)

scalaVersion := "3.7.0"

(Compile / scalaSource) := { baseDirectory.value / "src" }
(Compile / javaSource)  := { baseDirectory.value / "src" }

scalacOptions ++= Seq("-deprecation", "-unchecked", "-Wunused:linted", "-Xfatal-warnings", "-encoding", "us-ascii")
javacOptions  ++= Seq("-g", "-deprecation", "-Xlint:all", "-encoding", "us-ascii")

name := "Hello-Extension"

netLogoVersion      := "7.0.3"
netLogoClassManager := "org.nlogo.extensions.helloscala.HelloScalaExtension"
netLogoExtName      := "helloscala"
