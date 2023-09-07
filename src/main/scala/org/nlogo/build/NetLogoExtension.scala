package org.nlogo.build

import java.io.File

import sbt._
import sbt.Keys._
import sbt.io.{ CopyOptions }
import sbt.plugins.JvmPlugin
import sbt.internal.inc.classpath.ClasspathUtil

import sbt.librarymanagement.{ ModuleID, UpdateReport }

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
      IO.zip(zipMap, baseDir / s"$extName.zip", None)
    }

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
    val netLogoVersion          = settingKey[String]("version of NetLogo to depend on")
    val netLogoExtName          = settingKey[String]("extension-name")
    val netLogoClassManager     = settingKey[String]("extension-class-manager")
    val netLogoTarget           = settingKey[Target]("extension-target")
    val netLogoPackageExtras    = settingKey[Seq[(File, Option[String])]]("extension-package-extras")
    val netLogoTestExtras       = settingKey[Seq[File]]("extension-test-extras")
    val netLogoZipExtras        = settingKey[Seq[File]]("extension-zip-extras")
    val netLogoShortDescription = settingKey[String]("extension-short-description")
    val netLogoLongDescription  = settingKey[String]("extension-long-description")
    val netLogoHomepage         = settingKey[String]("extension-homepage")
    val netLogoJar              = settingKey[Option[File]]("extension-netlogo-jar-file-path for overriding the default resolver using the netlogo.jar.file system property")
    val packageZip              = taskKey[File]("package to zip file for publishing via the extension manager")
  }

  lazy val netLogoAPIVersion      = taskKey[String]("APIVersion of NetLogo associated with compilation target")
  lazy val netLogoDependencies    = settingKey[Seq[ModuleID]]("NetLogo libraries and dependencies")
  lazy val crossProjectID         = settingKey[ModuleID]("The cross-project ModuleID version of the projectID setting")
  lazy val extensionTestDirectory = settingKey[File]("extension-test-directory")
  lazy val extensionTestTarget    = settingKey[Target]("extension-test-target")

  import autoImport._

  val netLogoPackagedFiles = taskKey[Seq[(File, String)]]("extension-packaged-files")

  lazy val netLogoTestDirectory = settingKey[File]("directory that extension is moved to for testing")

  def directoryTarget(targetDirectory: File): Target =
    new DirectoryTarget(targetDirectory)

  // for our simple purposes we only need the organization and name to determine module equality
  // so we use cases class to handle that for us.   -Jeremy B February 2022

  case class MiniModule(organization: String, name: String)
  case class CalledModule(module: MiniModule, callers: Set[MiniModule])

  def miniturizeModule(module: ModuleID): MiniModule = MiniModule(module.organization, module.name)

  type RootsMap = Map[MiniModule, Set[MiniModule]]

  def rootsBabyRoots(projectIDs: Set[MiniModule], allModules: Map[MiniModule, CalledModule], depStack: Seq[MiniModule], rootsSoFar: RootsMap, moduleReport: CalledModule): RootsMap = {
    // if the rootsSoFar already has this module, we were processed as the caller of a prior module
    if (rootsSoFar.contains(moduleReport.module)) {
      rootsSoFar
    } else {
      val nonProjectCallers  = moduleReport.callers.filter(!projectIDs.contains(_))
      val nonCircularCallers = nonProjectCallers.filter(!depStack.contains(_))
      val callers            = nonCircularCallers.flatMap(allModules.get(_))
      val newDepStack        = depStack :+ moduleReport.module
      val newRoots           = callers.foldLeft(rootsSoFar)( (newRoots, callerReport) =>
        rootsBabyRoots(projectIDs, allModules, newDepStack, newRoots, callerReport)
      )

      val callerRoots = callers.map( (callerReport) => {
        val callerRoots = newRoots.getOrElse(callerReport.module, throw new Exception(s"Did not find a roots entry for $callerReport?"))
        // if it has no callers, it's the root.
        if (callerRoots.isEmpty) {
          Seq(callerReport.module)
        } else {
          callerRoots
        }
      })
      val moduleRoots = callerRoots.flatten.toSet
      newRoots + (moduleReport.module -> moduleRoots)
    }
  }

  def createRootsMap(projectIDs: Set[MiniModule], moduleReports: Seq[CalledModule]): RootsMap = {
    val allModules       = moduleReports.map( (m) => (m.module, m) ).toMap
    val reportsToRootMap = (rootsSoFar: RootsMap, moduleReport: CalledModule) => rootsBabyRoots(projectIDs, allModules, Seq(), rootsSoFar, moduleReport)
    moduleReports.foldLeft(Map[MiniModule, Set[MiniModule]]())(reportsToRootMap)
  }

  def isNetLogoDependency(miniNetLogoDeps: Set[MiniModule], rootsMap: RootsMap, module: MiniModule): Boolean = {
    val roots = rootsMap.getOrElse(module, throw new Exception(s"This module does not have a set of roots to inspect?  $module"))
    miniNetLogoDeps.contains(module) || roots.exists( (r) => miniNetLogoDeps.contains(r) )
  }

  def getExtensionDependencies(projectIDs: Set[ModuleID], netLogoDependencies: Seq[ModuleID], report: UpdateReport): Seq[File] = {
    val miniProjectIDs       = projectIDs.map(miniturizeModule).toSet
    val compileConfiguration = report.configurations.filter(_.configuration.name == "compile").headOption.getOrElse(throw new Exception("No compile configuration in the project?"))
    val miniNetLogoDeps      = netLogoDependencies.map(miniturizeModule).toSet
    val modules              = compileConfiguration.modules.map( (m) => {
      val module  = miniturizeModule(m.module)
      // It's possible some other lib directly used by the extension has called for the NetLogo
      // dependencies, directly or transitively. just ignore that since we always consider
      // the NetLogo deps to be "root".  -Jeremy B
      val callers = if (miniNetLogoDeps.contains(module)) {
        Set[MiniModule]()
      } else {
        m.callers.map( (c) => miniturizeModule(c.caller) ).toSet
      }
      CalledModule(module, callers)
    })
    val rootsMap               = createRootsMap(miniProjectIDs, modules)
    val extensionModuleReports = compileConfiguration.modules.filter( (m) => !isNetLogoDependency(miniNetLogoDeps, rootsMap, miniturizeModule(m.module)) )
    extensionModuleReports.flatMap( (moduleReport) =>
      moduleReport.artifacts.find(_._1.`type` == "jar").orElse(moduleReport.artifacts.find(_._1.extension == "jar")).map(_._2)
    )
  }

  def getAllFiles(files: Seq[File]): Seq[File] = {
    files.flatMap( (file) => {
      if (file.isDirectory) {
        file.allPaths.filter(!_.isDirectory).get
      } else {
        Seq(file)
      }
    })
  }

  override lazy val projectSettings = Seq(

    netLogoExtName          := name.value,
    netLogoTarget           := NetLogoExtension.directoryTarget(baseDirectory.value),
    netLogoPackageExtras    := Seq(),
    netLogoTestExtras       := Seq(),
    netLogoZipExtras        := Seq(),
    netLogoShortDescription := name.value,
    netLogoLongDescription  := "",
    netLogoHomepage         := "",

    netLogoJar := {
      val maybePath = Option(System.getProperty("netlogo.jar.file"))
      maybePath.map( (p) => new File(p) ).filter( (f) => f.exists() )
    },

    netLogoPackagedFiles := {
      val projectIDs   = Set(projectID.value, crossProjectID.value)
      val report       = (Compile / updateFull).value
      val dependencies = getExtensionDependencies(projectIDs, netLogoDependencies.value, report).map( (d) => (d, d.getName) )
      val extras       = netLogoPackageExtras.value.map({ case (extraFile, maybeRename) => (extraFile, maybeRename.getOrElse(extraFile.getName)) })
      val extensionJar = (Compile / packageBin / artifactPath).value
      dependencies ++ extras :+ (extensionJar -> s"${netLogoExtName.value}.jar")
    },

    netLogoAPIVersion := {
      val loader = ClasspathUtil.makeLoader(
        Attributed.data((Compile / dependencyClasspath).value).map(_.toPath)
      , scalaInstance.value
      )
      loader
        .loadClass("org.nlogo.api.APIVersion")
        .getMethod("version")
        .invoke(null).asInstanceOf[String]
    },

    packageZip := {
      (Compile / packageBin).value
      val netLogoFiles = netLogoPackagedFiles.value
      val extraZipFiles = NetLogoExtension.getAllFiles(netLogoZipExtras.value).map( (file) => {
        val newFile = IO.relativize((new File(".")).toPath.toAbsolutePath.toFile, file).get
        (file, newFile)
      })
      val allFiles       = netLogoFiles ++ extraZipFiles
      val uniqueFiles    = allFiles.toSet
      val zipName        = s"${netLogoExtName.value}-${version.value}.zip"
      val packageZipFile = baseDirectory.value / zipName
      IO.zip(uniqueFiles, packageZipFile, None)
      val json =
        s"""|{
        |  name:             "${name.value}"
        |  codeName:         "${netLogoExtName.value}"
        |  shortDescription: "${netLogoShortDescription.value}"
        |  longDescription:  "${netLogoLongDescription.value}"
        |  version:          "${version.value}"
        |  homepage:         "${netLogoHomepage.value}"
        |  downloadURL:      "https://raw.githubusercontent.com/NetLogo/NetLogo-Libraries/6.1/extensions/$zipName"
        |}""".stripMargin
      val libJson = s"Here is a JSON entry appropriate for use in the `libraries.conf` file of the NetLogo-Libraries repo: \n$json"
      sbt.Keys.streams.value.log.info(libJson)
      packageZipFile
    },

    extensionTestDirectory := baseDirectory.value / "extensions" / netLogoExtName.value,
    extensionTestTarget    := NetLogoExtension.directoryTarget(extensionTestDirectory.value),

    packageOptions +=
      Package.ManifestAttributes(
        ("Extension-Name",                netLogoExtName.value),
        ("Class-Manager",                 netLogoClassManager.value),
        ("NetLogo-Extension-API-Version", netLogoAPIVersion.value)
      ),

    (Compile / packageBin) := (Def.taskDyn {
      val jar = (Compile / packageBin).value

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
    },

    resolvers += "netlogo" at "https://dl.cloudsmith.io/public/netlogo/netlogo/maven/",

    // We do this little local file override so that bundled extensions can be easily built and tested against work in
    // progress in the NetLogo repo.  There might be other uses for it, too.  The odd bit is we don't map the tests jar,
    // too, because NetLogo runs the extension language tests directly, so it shouldn't be necessary.  If that turns out
    // to be wrong, this can be updated to do the tests as well.  -Jeremy B March 2023

    // Turns out the main jar needs to be unmanaged when the property is set, because the default behavior of sbt and
    // Coursier is to use the jar from cache, even when a `from URL` path is used.  It feels a little hacky, but it
    // worked in testing, so we'll go with it.  -Jeremy B September 2023
    (Compile / unmanagedJars) ++= netLogoJar.value.map( (f) => Seq(f).classpath ).getOrElse(Seq()),
    netLogoDependencies := netLogoJar.value.map( (_) => Seq() ).getOrElse(Seq("org.nlogo" % "netlogo" % netLogoVersion.value))
    ++ Seq(
      "org.nlogo"          %  "netlogo"    % netLogoVersion.value % Test classifier "tests"
    , "org.scalatest"      %% "scalatest"  % "3.2.10" % Test
    , "org.jogamp.jogl"    %  "jogl-all"   % "2.4.0" from "https://jogamp.org/deployment/archive/rc/v2.4.0/jar/jogl-all.jar"
    , "org.jogamp.gluegen" %  "gluegen-rt" % "2.4.0" from "https://jogamp.org/deployment/archive/rc/v2.4.0/jar/gluegen-rt.jar"
    ),

    libraryDependencies ++= netLogoDependencies.value,

    exportJars := true,

    crossProjectID := CrossVersion(scalaVersion.value, scalaBinaryVersion.value)(projectID.value),

    (Test / testOptions) ++= Seq(

      Tests.Setup( () => {
        (Compile / packageBin).value: @sbtUnchecked
        val files = netLogoPackagedFiles.value: @sbtUnchecked
        extensionTestTarget.value.create(files)

        def copyTestFile(file: File): Unit = {
          val testFile = (extensionTestDirectory.value / IO.relativize(baseDirectory.value, file).get)
          IO.copyFile(file, testFile)
        }

        val testFiles = NetLogoExtension.getAllFiles(netLogoTestExtras.value: @sbtUnchecked)
        testFiles.foreach(copyTestFile(_))
      }),

      Tests.Cleanup( () => {
        IO.delete(extensionTestDirectory.value)
      })

    )

  )
}
