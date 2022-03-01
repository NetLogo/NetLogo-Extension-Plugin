import org.nlogo.build.NetLogoExtension

enablePlugins(NetLogoExtension)

version    := "0.0.1"
isSnapshot := true

scalaVersion            := "2.12.12"
(Compile / scalaSource) := { baseDirectory.value / "src" / "main" }
(Test / scalaSource)    := { baseDirectory.value / "src" / "test" }
scalacOptions ++= Seq("-deprecation", "-unchecked", "-Xlint", "-Xfatal-warnings", "-encoding", "us-ascii")

name := "Hello-Extension"

libraryDependencies ++= Seq("commons-lang" % "commons-lang" % "2.6")

netLogoVersion       :=  "6.2.2"
netLogoClassManager  :=  "org.nlogo.extensions.helloscala.HelloScalaExtension"
netLogoExtName       :=  "helloscala"
netLogoZipSources    :=  false
netLogoPackageExtras +=  (baseDirectory.value / "resources" / "include_me_1.txt", None)
netLogoTestExtras    ++= Seq(baseDirectory.value / "test-docs", baseDirectory.value / "test_me_2.txt")
netLogoZipExtras     ++= Seq(baseDirectory.value / "test-docs", baseDirectory.value / "test_me_2.txt")

// for testing the creation of the zip file
val unzipPackage = taskKey[File]("unzip the created zip file for checking")
unzipPackage := {
  val zipFile = baseDirectory.value / s"${netLogoExtName.value}-${version.value}.zip"
  val toDirectory = baseDirectory.value / s"${netLogoExtName.value}-${version.value}-unzipped"
  IO.unzip(zipFile, toDirectory)
  toDirectory
}
