import org.nlogo.build.NetLogoExtension

enablePlugins(NetLogoExtension)

version    := "0.0.1"
isSnapshot := true

scalaVersion            := "2.12.12"
(Compile / scalaSource) := { baseDirectory.value / "src" / "main" }
(Test / scalaSource)    := { baseDirectory.value / "src" / "test" }
scalacOptions ++= Seq("-deprecation", "-unchecked", "-Xlint", "-Xfatal-warnings", "-encoding", "us-ascii")

name := "Hello-Extension"

// while working on the vid extension, this plugin caused a duplicate entry for the `packageZip` command
// so included here for testing
javaCppVersion    :=  "1.5.7"
javaCppPresetLibs ++= Seq("opencv" -> "4.5.5", "openblas" -> "0.3.19")
javaCppPlatform   :=  Seq("windows-x86_64", "windows-x86", "macosx-arm64", "macosx-x86_64", "linux-x86", "linux-x86_64")

libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-lang3" % "3.11"
, "org.apache.commons" % "commons-text" % "1.9"
, "org.bytedeco" % "javacv" % "1.5.7"
)

netLogoVersion       :=  "6.2.2"
netLogoClassManager  :=  "org.nlogo.extensions.helloscala.HelloScalaExtension"
netLogoExtName       :=  "helloscala"
netLogoPackageExtras +=  (baseDirectory.value / "resources" / "include_me_1.txt", None)
netLogoTestExtras    ++= Seq(baseDirectory.value / "test-docs", baseDirectory.value / "test_me_2.txt")
netLogoZipExtras     ++= Seq(baseDirectory.value / "test-docs", baseDirectory.value / "test_me_2.txt")
netLogoShortDescription := "Hello Scala for Extension in NetLogo"
netLogoLongDescription  := "Very long text that most people won't read."
netLogoHomepage         := "https://github.com/NetLogo/NetLogo-Extension-Plugin"

// for testing the creation of the zip file
val unzipPackage = taskKey[File]("unzip the created zip file for checking")
unzipPackage := {
  val zipFile = baseDirectory.value / s"${netLogoExtName.value}-${version.value}.zip"
  val toDirectory = baseDirectory.value / s"${netLogoExtName.value}-${version.value}-unzipped"
  IO.unzip(zipFile, toDirectory)
  toDirectory
}
