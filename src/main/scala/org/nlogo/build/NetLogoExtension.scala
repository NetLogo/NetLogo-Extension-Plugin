import sbt._, Keys._

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

      packageBin in Compile <<= (packageBin in Compile, baseDirectory, streams, extName) map {
        (jar, base, s, name) =>
          IO.copyFile(jar, base / "%s.jar".format(name))
          Process(("pack200 --modification-time=latest --effort=9 --strip-debug " +
            "--no-keep-file-order --unknown-attribute=strip " +
            "%s.jar.pack.gz %s.jar").format(name, name)).!!
        if(Process("git diff --quiet --exit-code HEAD").! == 0) {
          Process("git archive -o %s.zip --prefix=%s/ HEAD".format(name, name)).!!
          IO.createDirectory(base / name)
          IO.copyFile(base / "%s.jar".format(name), base / name / "%s.jar".format(name))
          IO.copyFile(base / "%s.jar.pack.gz".format(name), base / name / "%s.jar.pack.gz".format(name))
          Process("zip %s.zip %s/%s.jar %s/%s.jar.pack.gz".format(Seq.fill(5)(name): _*)).!!
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
