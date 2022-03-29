package org.nlogo.build

import java.io.File

import sbt._
import sbt.Keys._
import sbt.io.{ CopyOptions }
import sbt.plugins.JvmPlugin
import sbt.internal.inc.classpath.ClasspathUtilities

import sbt.librarymanagement.{ ModuleID, OrganizationArtifactReport, UpdateReport }
import sbt.io.NameFilter

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
    val netLogoPackageExtras = settingKey[Seq[(File, Option[String])]]("extension-package-extras")
    val netLogoTestExtras    = settingKey[Seq[File]]("extension-test-extras")
    val netLogoZipExtras     = settingKey[Seq[File]]("extension-zip-extras")
    val packageZip           = taskKey[File]("package to zip file for publishing via the extension manager")
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

    netLogoExtName       := name.value,
    netLogoTarget        := NetLogoExtension.directoryTarget(baseDirectory.value),
    netLogoZipSources    := true,
    netLogoPackageExtras := Seq(),
    netLogoTestExtras    := Seq(),
    netLogoZipExtras     := Seq(),

    netLogoPackagedFiles := {
      val dependencies = getExtensionDependencies(Set(projectID.value, crossProjectID.value), netLogoDependencies.value, (Compile / updateFull).value).map( (d) => (d, d.getName) )
      val extras       = netLogoPackageExtras.value.map({ case (extraFile, maybeRename) => (extraFile, maybeRename.getOrElse(extraFile.getName)) })
      val extensionJar = (Compile / packageBin / artifactPath).value
      dependencies ++ extras :+ (extensionJar -> s"${netLogoExtName.value}.jar")
    },

    netLogoAPIVersion := {
      val loader = ClasspathUtilities.makeLoader(Attributed.data((Compile / dependencyClasspath).value), scalaInstance.value)
      loader
        .loadClass("org.nlogo.api.APIVersion")
        .getMethod("version")
        .invoke(null).asInstanceOf[String]
    },

    packageZip := {
      (Compile / packageBin).value
      val netLogoFiles = netLogoPackagedFiles.value
      val extraZipFiles = NetLogoExtension.getAllFiles(netLogoZipExtras.value).map( (file) => {
        val newFile = IO.relativize(baseDirectory.value, file).get
        (file, newFile)
      })
      val packageZipFile = baseDirectory.value / s"${netLogoExtName.value}-${version.value}.zip"
      IO.zip(netLogoFiles ++ extraZipFiles, packageZipFile)
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

    netLogoDependencies := Seq(
      "org.nlogo"          %  "netlogo"    % netLogoVersion.value
    , "org.nlogo"          %  "netlogo"    % netLogoVersion.value % Test classifier "tests"
    , "org.scalatest"      %% "scalatest"  % "3.2.10" % Test
    , "org.jogamp.jogl"    %  "jogl-all"   % "2.4.0" from "https://jogamp.org/deployment/archive/rc/v2.4.0-rc-20210111/jar/jogl-all.jar"
    , "org.jogamp.gluegen" %  "gluegen-rt" % "2.4.0" from "https://jogamp.org/deployment/archive/rc/v2.4.0-rc-20210111/jar/gluegen-rt.jar"
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
        val files = netLogoPackagedFiles.value: @sbtUnchecked
        IO.delete(extensionTestDirectory.value)
      })

    )

  )
}
