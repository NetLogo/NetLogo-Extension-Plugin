package org.nlogo.build

import sbt._, Keys._, plugins.JvmPlugin,
  io.CopyOptions,
  internal.inc.classpath.ClasspathUtilities

import scala.sys.process.Process

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
        val allFiles = Process(s"git ls-files", baseDir).lineStream_!.filterNot(_ == ".gitignore")
        allFiles.map(new File(_)) zip allFiles
      } else
        Seq()
  }

  class DirectoryTarget(baseDir: File) extends Target {
    override def producedFiles(fileMap: Seq[(File, String)]): Seq[File] =
      fileMap.map(_._2).map(baseDir / _).filterNot(fileMap.contains) :+ baseDir / ".bundledFiles"

    override def create(sourceMap: Seq[(File, String)]): Unit = {
      val files = sourceMap.map {
        case (file, name) => (file, baseDir / name)
      }
      IO.write(baseDir / ".bundledFiles",
        sourceMap
          .map { t => t._1.getAbsolutePath + "->" + t._2 }
          .mkString("\n"))
      IO.copy(files, CopyOptions().withOverwrite(true))
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

  lazy val netLogoAPIVersion = taskKey[String]("APIVersion of NetLogo associated with compilation target")

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
          !path.getName.startsWith("NetLogo") &&
          !path.getName.startsWith("netlogo"))
        .map(path => (path, path.getName))
    },

    artifactName := ((_, _, _) => s"${netLogoExtName.value}.jar"),

    netLogoPackagedFiles := {
      netLogoPackageExtras.value :+ ((artifactPath in packageBin in Compile).value -> s"${netLogoExtName.value}.jar")
    },

    netLogoAPIVersion := {
      val loader = ClasspathUtilities.makeLoader(
        Attributed.data((dependencyClasspath in Compile).value),
        scalaInstance.value)
      loader
        .loadClass("org.nlogo.api.APIVersion")
        .getMethod("version")
        .invoke(null).asInstanceOf[String]
    },

    packageOptions +=
      Package.ManifestAttributes(
        ("Extension-Name",                netLogoExtName.value),
        ("Class-Manager",                 netLogoClassManager.value),
        ("NetLogo-Extension-API-Version", netLogoAPIVersion.value)
      ),

    packageBin in Compile := (Def.taskDyn {
        val jar = (packageBin in Compile).value

        if (isSnapshot.value || Process("git diff --quiet --exit-code HEAD", baseDirectory.value).! == 0) {
          Def.task {
            netLogoTarget.value.create(netLogoPackagedFiles.value)
            jar
          }
        } else {
          Def.task {
            streams.value.log.warn("working tree not clean when packaging; target not created")
            IO.delete(netLogoTarget.value.producedFiles(netLogoPackagedFiles.value))
            jar
          }
        }
    }).value,

    clean := {
      val _ = clean.value
      IO.delete(netLogoTarget.value.producedFiles(netLogoPackagedFiles.value))
    }

  ) ++ netLogoJarSettings

  def netLogoJarSettings: Seq[Setting[_]] = {

    val netLogoJarFile =
      Option(System.getProperty("netlogo.jar.file"))
        .map { f =>
          val jar = file(f)
          val testJar = file(f.replaceAllLiterally(".jar", "-tests.jar"))
          Seq(unmanagedJars in Compile ++= Seq(jar, testJar))
        }

    val netLogoJarURL =
      Option(System.getProperty("netlogo.jar.url"))
        .map { url =>
          val urlVersion = url.split("/").last
            .stripPrefix("NetLogo")
            .stripPrefix("-")
            .stripSuffix(".jar")
          val version = if (urlVersion == "") "DEV" else urlVersion
          val testUrl = url.replaceAllLiterally(".jar", "-tests.jar")
          Seq(libraryDependencies ++= Seq(
            "org.nlogo" % "NetLogo" % version changing() from url,
            "org.nlogo" % "NetLogo-tests" % version changing() from testUrl))
        }

    (netLogoJarFile orElse netLogoJarURL).getOrElse {
      Seq(
        resolvers += "netlogo" at "https://dl.cloudsmith.io/public/netlogo/netlogo/maven/"
      , libraryDependencies ++= Seq(
          "org.nlogo"          %  "netlogo"    % netLogoVersion.value
        , "org.nlogo"          %  "netlogo"    % netLogoVersion.value % Test classifier "tests"
        , "org.scalatest"      %% "scalatest"  % "3.2.10" % Test
        , "org.jogamp.jogl"    %  "jogl-all"   % "2.4.0" from "https://jogamp.org/deployment/archive/rc/v2.4.0-rc-20210111/jar/jogl-all.jar"
        , "org.jogamp.gluegen" %  "gluegen-rt" % "2.4.0" from "https://jogamp.org/deployment/archive/rc/v2.4.0-rc-20210111/jar/gluegen-rt.jar"
        )
      )
    }
  }

  def directoryTarget(targetDirectory: File): Target =
    new DirectoryTarget(targetDirectory)
}
