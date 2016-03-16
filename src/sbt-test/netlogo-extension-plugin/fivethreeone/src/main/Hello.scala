import org.nlogo.api._
import org.nlogo.api.Syntax._
import org.nlogo.api.ScalaConversions._

class HelloScalaExtension extends DefaultClassManager {
  def load(manager: PrimitiveManager) {
    manager.addPrimitive("hello", new HelloString)
  }
}

class HelloString extends DefaultReporter {
  override def getSyntax = reporterSyntax(Array(StringType), StringType)
  def report(args: Array[Argument], context: Context): AnyRef = {
    val name = try args(0).getString
    catch {
      case e: LogoException =>
        throw new ExtensionException(e.getMessage)
    }
    "hello, " + name
  }
}
