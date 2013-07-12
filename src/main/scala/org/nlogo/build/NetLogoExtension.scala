import sbt._, Process._, Keys._

object Plugin extends Plugin {

  object NetLogoExtension {

    val extName      = SettingKey[String]("extension-name")
    val classManager = SettingKey[String]("extension-class-manager")

    val settings = Seq(

      extName <<= name,

      artifactName <<= extName { name => (_, _, _) => "%s.jar".format(name) },

      packageOptions <<= (extName, classManager) map { (name, cm) =>
        Seq(
          Package.ManifestAttributes(
            ("Extension-Name", name),
            ("Class-Manager",  cm),
            ("NetLogo-Extension-API-Version", "5.0")
          )
        )
      },

      packageBin in Compile <<= (packageBin in Compile, dependencyClasspath in Runtime, baseDirectory, streams, extName) map {
        (jar, classpath, base, s, name) =>

        val libraryJarPaths =
          classpath.files.filter (path =>
            path.getName.endsWith(".jar") &&
            path.getName != "scala-library.jar" &&
            !path.getName.startsWith("NetLogo"))

        IO.copyFile(jar, base / "%s.jar".format(name))
        libraryJarPaths foreach (path => IO.copyFile(path, base / path.getName))

        (libraryJarPaths.map(_.getName) :+ "%s.jar".format(name)) foreach {
          n =>
            val cmd = ("pack200 --modification-time=latest --effort=9 --strip-debug " +
              "--no-keep-file-order --unknown-attribute=strip %s.pack.gz %s").format(n, n)
            Process(cmd).!!
        }

        if(Process("git diff --quiet --exit-code HEAD").! == 0) {
          Process("git archive -o %s.zip --prefix=%s/ HEAD".format(name, name)).!!
          IO.createDirectory(base / name)
          val zipExtraJars = libraryJarPaths.map(_.getName) :+ "%s.jar".format(name)
          val zipExtras    = zipExtraJars flatMap (x => List(x, x + ".pack.gz"))
          zipExtras foreach (extra => IO.copyFile(base / extra, base / name / extra))
          Process("zip -r %s.zip ".format(name) + zipExtras.map(name + "/" + _).mkString(" ")).!!
          IO.delete(base / name)
        }
        else {
          s.log.warn("working tree not clean; no zip archive made")
          IO.delete(base / "%s.zip".format(name))
        }

        jar

      },

      cleanFiles <++= (baseDirectory, extName) { (base, name) =>
        Seq(base / "%s.jar".format(name),
          base / "%s.jar.pack.gz".format(name),
          base / "%s.zip".format(name))
      }

    )

  }

}
