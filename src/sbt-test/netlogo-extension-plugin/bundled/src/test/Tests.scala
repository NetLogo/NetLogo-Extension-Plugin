package org.nlogo.extensions.helloscala

import org.nlogo.headless.TestLanguage

class Tests extends TestLanguage(Seq(new java.io.File("tests.txt").getCanonicalFile)) {
  println(java.nio.file.Paths.get("").toAbsolutePath().toString())
  println(System.getProperty("user.home"))
  System.setProperty("org.nlogo.preferHeadless", "true")
}
