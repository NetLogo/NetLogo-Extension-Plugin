import org.nlogo.build.NetLogoExtension

enablePlugins(NetLogoExtension)

version    := "0.0.1"
isSnapshot := true

scalaVersion           := "2.12.12"
scalaSource in Compile := { baseDirectory.value / "src" / "main" }
scalaSource in Test    := { baseDirectory.value / "src" / "test" }
scalacOptions ++= Seq("-deprecation", "-unchecked", "-Xlint", "-Xfatal-warnings", "-encoding", "us-ascii")

name := "Hello-Extension"

libraryDependencies ++= Seq("commons-lang" % "commons-lang" % "2.6")

netLogoVersion       := "6.2.2"
netLogoClassManager  := "org.nlogo.extensions.helloscala.HelloScalaExtension"
netLogoExtName       := "helloscala"
netLogoZipSources    := false
netLogoTarget        := NetLogoExtension.directoryTarget(baseDirectory.value)
netLogoPackageExtras += (baseDirectory.value / "resources" / "include_me_1.txt", None)
