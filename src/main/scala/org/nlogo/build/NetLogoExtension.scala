package org.nlogo.build

import sbt._, Keys._, plugins.JvmPlugin

object NetLogoExtension extends AutoPlugin {

  // this is needed because we override settings like `packageBin`, see below,
  // which are populated by JvmPlugin. -- RG 6/22/15
  override def requires: Plugins = JvmPlugin

  object autoImport {
    val netLogoExtName      = settingKey[String]("extension-name")
    val netLogoClassManager = settingKey[String]("extension-class-manager")
    val netLogoZipSources   = settingKey[Boolean]("extension-zip-sources")
    val netLogoZipExtras    = taskKey[Seq[(File, String)]]("extension-zip-extras")
  }

  import autoImport._

  override lazy val projectSettings = Seq(

    netLogoExtName := name.value,

    netLogoZipSources := true,

    netLogoZipExtras := {
      (dependencyClasspath in Runtime).value.files
        .filter(path =>
          path.getName.endsWith(".jar") &&
          !path.getName.startsWith("scala-library") &&
          !path.getName.startsWith("NetLogo"))
        .map(path => (path, path.getName))
    },

    artifactName := ((_, _, _) => s"${name.value}.jar"),

    packageOptions +=
      Package.ManifestAttributes(
        ("Extension-Name", netLogoExtName.value),
        ("Class-Manager",  netLogoClassManager.value),
        ("NetLogo-Extension-API-Version", "5.0")
      ),

    packageBin in Compile := {

        val baseDir = baseDirectory.value
        val jar     = (packageBin in Compile).value
        val name    = netLogoExtName.value

        if(isSnapshot.value || Process("git diff --quiet --exit-code HEAD", baseDir).! == 0) {
          val sourcesToZip: Seq[(File, String)] =
            if (netLogoZipSources.value) {
              val allFiles = Process(s"git ls-files").lines_!.filterNot(_ == ".gitignore")
              allFiles.map(new File(_)) zip allFiles
            } else
              Seq()
          val zipMap = (sourcesToZip ++ netLogoZipExtras.value :+ (jar -> s"$name.jar")).map {
            case (file, zipPath) => (file, s"$name/$zipPath")
          }
          IO.zip(zipMap, baseDir / s"$name.zip")
        }
        else {
          streams.value.log.warn("working tree not clean when packaging; no zip archive made")
          IO.delete(baseDir / s"$name.zip")
        }

        jar

    },

    cleanFiles ++=
      Seq(
        baseDirectory.value / s"${netLogoExtName.value}.jar",
        baseDirectory.value / s"${netLogoExtName.value}.zip"
      )

  )

}
