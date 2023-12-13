package files.reference

import utils.*
import Bundle.*, Tags.*

object Index extends ReferencePage {

  override def pageSettings =
    super.pageSettings.withTitle("Reference")

  override def blogSettings =
    super.blogSettings.withSections(firstSection)

  val firstSection = Section(
    s"${Consts.ProjectName} reference",
    div(
      s"""
      Simple types: `Int`, `Double`, `Boolean`, `String` etc work out of the box.
      """.md,
      chl.scala(s"""
      import ba.sake.tupson.{given, *}
      
      // write a value to JSON string
      val myValue = 123
      println(123.toJson) // 123

      // parse a value from JSON string
      val myParsedValue = ${tq}123${tq}.parseJson[Int]
      println(myParsedValue) // 123
      """)
    )
  )
}
