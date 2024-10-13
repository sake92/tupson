package files.reference

import utils.*
import Bundle.*, Tags.*

object Options extends ReferencePage {

  override def pageSettings =
    super.pageSettings.withTitle("Options")

  override def blogSettings =
    super.blogSettings.withSections(firstSection)

  val firstSection = Section(
    s"Options",
    div(
      s"""        
    `Option[T]` work as you expect.  
    `None` corresponds to JSON's `null`.  
    
    Note that you need a `JsonRW[T]` given instance.
    """.md,
      chl.scala(s"""
        Option.empty[String].toJson
        // null

        Option("str").toJson
        // "str"
        """),
      chl.scala(s"""
        ${tq} null ${tq}.parseJson[Option[String]]
        // None

        ${tq} "str" ${tq}.parseJson[Option[String]]
        // Some(str)
        """)
    )
  )
}
