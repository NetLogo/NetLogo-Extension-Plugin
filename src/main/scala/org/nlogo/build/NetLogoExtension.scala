package org.nlogo.build

import java.io.File

import sbt._, Keys._, plugins.JvmPlugin,
  io.CopyOptions,
  internal.inc.classpath.ClasspathUtilities

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
    val netLogoPackageExtras = taskKey[Seq[(File, String)]]("extension-package-extras")
  }

  lazy val netLogoAPIVersion = taskKey[String]("APIVersion of NetLogo associated with compilation target")

  lazy val netLogoDependencies = settingKey[Seq[ModuleID]]("NetLogo libraries and dependencies")

  lazy val crossProjectID = settingKey[ModuleID]("The cross-project ModuleID version of the projectID setting")

  import autoImport._

  val netLogoPackagedFiles = taskKey[Seq[(File, String)]]("extension-packaged-files")

  // for our simple purposes we only need the organization and name to determine module equality
  // so we use cases class to handle that for us.   -Jeremy B February 2022

  case class MiniModule(organization: String, name: String)
  case class CalledModule(module: MiniModule, callers: Set[MiniModule])

  def miniturizeModule(module: ModuleID): MiniModule = MiniModule(module.organization, module.name)

  type RootsMap = Map[MiniModule, Set[MiniModule]]

  def rootsBabyRoots(projectIDs: Set[MiniModule], allModules: Map[MiniModule, CalledModule], rootsSoFar: RootsMap, moduleReport: CalledModule): RootsMap = {
    // if the rootsSoFar already has this module, we were processed as the caller of a prior module
    if (rootsSoFar.contains(moduleReport.module)) {
      rootsSoFar
    } else {
      val nonProjectCallers = moduleReport.callers.filter(!projectIDs.contains(_))
      val callers           = nonProjectCallers.flatMap(allModules.get(_))
      val newRoots          = callers.foldLeft(rootsSoFar)( (newRoots, callerReport) => rootsBabyRoots(projectIDs, allModules, newRoots, callerReport) )
      val callerRoots       = callers.map( (callerReport) => {
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
    val reportsToRootMap = (rootsSoFar: RootsMap, moduleReport: CalledModule) => rootsBabyRoots(projectIDs, allModules, rootsSoFar, moduleReport)
    moduleReports.foldLeft(Map[MiniModule, Set[MiniModule]]())(reportsToRootMap)
  }

  def isNetLogoDependency(miniNetLogoDeps: Set[MiniModule], rootsMap: RootsMap, module: MiniModule): Boolean = {
    val roots = rootsMap.getOrElse(module, throw new Exception(s"This module does not have a set of roots to inspect?  $module"))
    miniNetLogoDeps.contains(module) || roots.exists( (r) => miniNetLogoDeps.contains(r) )
  }

  def getExtensionDependencies(projectIDs: Set[ModuleID], netLogoDependencies: Seq[ModuleID], report: UpdateReport): Seq[File] = {
    val miniProjectIDs         = projectIDs.map(miniturizeModule).toSet
    val compileConfiguration   = report.configurations.filter(_.configuration.name == "compile").headOption.getOrElse(throw new Exception("No compile configuration in the project?"))
    val modules                = compileConfiguration.modules.map( (m) => CalledModule(miniturizeModule(m.module), m.callers.map( (c) => miniturizeModule(c.caller) ).toSet) )
    val rootsMap               = createRootsMap(miniProjectIDs, modules)
    val miniNetLogoDeps        = netLogoDependencies.map(miniturizeModule).toSet
    val extensionModuleReports = compileConfiguration.modules.filter( (m) => !isNetLogoDependency(miniNetLogoDeps, rootsMap, miniturizeModule(m.module)) )
    extensionModuleReports.flatMap( (moduleReport) =>
      moduleReport.artifacts.find(_._1.`type` == "jar").orElse(moduleReport.artifacts.find(_._1.extension == "jar")).map(_._2)
    )
  }

  override lazy val projectSettings = Seq(

    netLogoExtName := name.value,

    netLogoTarget :=
      new ZipTarget(netLogoExtName.value, baseDirectory.value, netLogoZipSources.value),

    netLogoZipSources := true,

    netLogoPackageExtras := getExtensionDependencies(Set(projectID.value, crossProjectID.value), netLogoDependencies.value, (Compile / updateFull).value).map( (d) => (d, d.getName) ),

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

    crossProjectID := CrossVersion(scalaVersion.value, scalaBinaryVersion.value)(projectID.value)

  )

  def directoryTarget(targetDirectory: File): Target =
    new DirectoryTarget(targetDirectory)

}
