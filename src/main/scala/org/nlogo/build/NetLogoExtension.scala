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

    netLogoExtName <<= name,

    netLogoZipSources := true,

    artifactName <<= netLogoExtName { name => (_, _, _) => "%s.jar".format(name) },

    packageOptions <<= (netLogoExtName, netLogoClassManager) map { (name, cm) =>
      Seq(
        Package.ManifestAttributes(
          ("Extension-Name", name),
          ("Class-Manager",  cm),
          ("NetLogo-Extension-API-Version", "5.0")
        )
      )
    },

    packageBin in Compile <<= (
      packageBin in Compile,
      dependencyClasspath in Runtime,
      baseDirectory,
      streams,
      netLogoExtName,
      netLogoZipSources) map {
      (jar, classpath, base, s, name, zipSources) =>

        val libraryJarPaths =
          classpath.files.filter (path =>
            path.getName.endsWith(".jar") &&
            !path.getName.startsWith("scala-library") &&
            !path.getName.startsWith("NetLogo"))

        IO.copyFile(jar, base / "%s.jar".format(name))
        libraryJarPaths foreach (path => IO.copyFile(path, base / path.getName))

        if(Process("git diff --quiet --exit-code HEAD").! == 0) {
          if (zipSources) {
            Process("git archive -o %s.zip --prefix=%s/ HEAD".format(name, name)).!!
          }
          val zipExtras = libraryJarPaths.map(_.getName) :+ "%s.jar".format(name)
          zipExtras foreach (extra => IO.copyFile(base / extra, base / name / extra))
          val cmd = "zip -r %s.zip ".format(name) + zipExtras.map(name + "/" + _).mkString(" ")
          Process(cmd).!!
          IO.delete(base / name)
        }
        else {
          s.log.warn("working tree not clean; no zip archive made")
          IO.delete(base / "%s.zip".format(name))
        }

        jar

    },

    cleanFiles <++= (baseDirectory, netLogoExtName) { (base, name) =>
      Seq(base / "%s.jar".format(name),
        base / "%s.zip".format(name))
    }

  )

}
