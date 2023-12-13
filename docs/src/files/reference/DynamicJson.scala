package files.reference

import utils.*
import Bundle.*, Tags.*

object DynamicJson extends ReferencePage {

  override def pageSettings =
    super.pageSettings.withTitle("Dynamic JSON")

  override def blogSettings =
    super.blogSettings.withSections(firstSection)

  val firstSection = Section(
    s"Dynamic JSON",
    div(
      s"""
      Sometimes you don't know exactly the structure of incoming JSON payload.  
      For that you can use [Jawn's](https://github.com/typelevel/jawn) JValue:
      """.md,
      chl.scala("""
      import org.typelevel.jawn.ast.JValue

      case class MyData(
        str: String,
        dynamic: JValue // this key's data can be anything: null, string, object, sequence...
      ) derives JsonRW

      """)
    )
  )
}
