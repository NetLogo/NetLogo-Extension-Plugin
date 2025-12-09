package org.nlogo.extensions.helloscala

import org.nlogo.api.{ Argument, Context, DefaultClassManager, ExtensionException, LogoException, PrimitiveManager,
                       Reporter }
import org.nlogo.core.Syntax

class HelloScalaExtension extends DefaultClassManager {
  override def load(manager: PrimitiveManager): Unit = {
    manager.addPrimitive("hello", new HelloString)
  }
}

class HelloString extends Reporter {
  override def getSyntax: Syntax =
    Syntax.reporterSyntax(right = List(Syntax.StringType), ret = Syntax.StringType)

  def report(args: Array[Argument], context: Context): AnyRef = {
    val name: String = try {
      args(0).getString
    } catch {
      case e: LogoException =>
        throw new ExtensionException(e.getMessage)
    }

    s"hello, $name"
  }
}
