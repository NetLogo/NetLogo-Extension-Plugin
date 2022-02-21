import org.nlogo.core.Syntax.{ reporterSyntax, StringType }
import org.nlogo.api.{ Argument, Context, DefaultClassManager, ExtensionException, LogoException, PrimitiveManager, Reporter }

class HelloScalaExtension extends DefaultClassManager {
  def load(manager: PrimitiveManager) {
    manager.addPrimitive("hello", new HelloString)
  }
}

class HelloString extends Reporter {
  override def getSyntax = reporterSyntax(right = List(StringType), ret = StringType)
  def report(args: Array[Argument], context: Context): AnyRef = {
    val name = try args(0).getString
    catch {
      case e: LogoException =>
        throw new ExtensionException(e.getMessage)
    }
    "hello, " + name
  }
}
