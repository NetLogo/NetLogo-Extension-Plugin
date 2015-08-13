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
  }

  import autoImport._

  override lazy val projectSettings = Seq(

    netLogoExtName := name.value,

    netLogoZipSources := true,

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

        val libraryJarPaths =
          (dependencyClasspath in Runtime).value.files.filter (path =>
            path.getName.endsWith(".jar") &&
            !path.getName.startsWith("scala-library") &&
            !path.getName.startsWith("NetLogo"))

        IO.copyFile(jar, baseDir / s"$name.jar")
        libraryJarPaths foreach (path => IO.copyFile(path, baseDir / path.getName))

        if(Process("git diff --quiet --exit-code HEAD").! == 0) {
          if (netLogoZipSources.value) {
            Process(s"git archive -o $name.zip --prefix=$name/ HEAD").!!
          }
          val zipExtras = libraryJarPaths.map(_.getName) :+ s"$name.jar"
          zipExtras foreach (extra => IO.copyFile(baseDir / extra, baseDir / name / extra))
          val extrasStr = zipExtras.map(name + "/" + _).mkString(" ")
          val cmd = s"zip -r $name.zip ${extrasStr}"
          Process(cmd).!!
          IO.delete(baseDir / name)
        }
        else {
          streams.value.log.warn("working tree not clean; no zip archive made")
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
