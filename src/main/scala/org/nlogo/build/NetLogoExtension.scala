package org.nlogo.build

import sbt._, Keys._, plugins.JvmPlugin

object NetLogoExtension extends AutoPlugin {

  trait Target {
    def producedFiles(fileMap: Seq[(File, String)]): Seq[File]
    def create(fileMap: Seq[(File, String)]): Unit
  }

  class ZipTarget(extName: String, baseDir: File, includeSources: Boolean) extends Target {
    override def producedFiles(fileMap: Seq[(File, String)]): Seq[File] =
      Seq(baseDir / s"$extName.zip")

    override def create(sourceMap: Seq[(File, String)]): Unit = {
      val zipMap = sourceMap.map { case (file, path) => (file, s"$extName/$path") }
      IO.zip(zipMap, baseDir / s"$extName.zip")
    }

    private def sourcesToZip: Seq[(File, String)] =
      if (includeSources) {
        val allFiles = Process(s"git ls-files", baseDir).lines_!.filterNot(_ == ".gitignore")
        allFiles.map(new File(_)) zip allFiles
      } else
        Seq()
  }

  class DirectoryTarget(baseDir: File) extends Target {
    override def producedFiles(fileMap: Seq[(File, String)]): Seq[File] =
      fileMap.map(_._2).map(baseDir / _).filterNot(fileMap.contains)

    override def create(sourceMap: Seq[(File, String)]): Unit = {
      val files = sourceMap.map {
        case (file, name) => (file, baseDir / name)
      }
      IO.copy(files, overwrite = true)
    }
  }

  // this is needed because we override settings like `packageBin`, see below,
  // which are populated by JvmPlugin. -- RG 6/22/15
  override def requires: Plugins = JvmPlugin

  object autoImport {
    val netLogoVersion       = settingKey[String]("version of NetLogo to depend on")
    val netLogoExtName       = settingKey[String]("extension-name")
    val netLogoClassManager  = settingKey[String]("extension-class-manager")
    val netLogoZipSources    = settingKey[Boolean]("extension-zip-sources")
    val netLogoTarget        = settingKey[Target]("extension-target")
    val netLogoPackageExtras = taskKey[Seq[(File, String)]]("extension-package-extras")
  }

  import autoImport._

  val netLogoPackagedFiles = taskKey[Seq[(File, String)]]("extension-packaged-files")

  override lazy val projectSettings = Seq(

    netLogoExtName := name.value,

    netLogoTarget :=
      new ZipTarget(netLogoExtName.value, baseDirectory.value, netLogoZipSources.value),

    netLogoZipSources := true,

    netLogoPackageExtras := {
      (externalDependencyClasspath in Runtime).value.files
        .filter(path =>
          path.getName.endsWith(".jar") &&
          !path.getName.startsWith("scala-library") &&
          !path.getName.startsWith("NetLogo"))
        .map(path => (path, path.getName))
    },

    artifactName := ((_, _, _) => s"${netLogoExtName.value}.jar"),

    netLogoPackagedFiles := {
      netLogoPackageExtras.value :+ ((artifactPath in packageBin in Compile).value -> s"${netLogoExtName.value}.jar")
    },

    packageOptions +=
      Package.ManifestAttributes(
        ("Extension-Name", netLogoExtName.value),
        ("Class-Manager",  netLogoClassManager.value),
        ("NetLogo-Extension-API-Version", "5.0")
      ),

    packageBin in Compile := {
        val jar = (packageBin in Compile).value

        if (isSnapshot.value || Process("git diff --quiet --exit-code HEAD", baseDirectory.value).! == 0) {
          netLogoTarget.value.create(netLogoPackagedFiles.value)
        } else {
          streams.value.log.warn("working tree not clean when packaging; target not created")
          IO.delete(netLogoTarget.value.producedFiles(netLogoPackagedFiles.value))
        }

        jar
    },

    clean := {
      clean.value
      IO.delete(netLogoTarget.value.producedFiles(netLogoPackagedFiles.value))
    },

    resolvers += Resolver.bintrayRepo("content/netlogo", "NetLogo-JVM"),

    libraryDependencies ++= Seq(
      "org.nlogo" % "netlogo" % netLogoVersion.value intransitive,
      "org.nlogo" % "netlogo" % netLogoVersion.value % "test" intransitive() classifier "tests")

  )

  def directoryTarget(targetDirectory: File): Target =
    new DirectoryTarget(targetDirectory)
}
