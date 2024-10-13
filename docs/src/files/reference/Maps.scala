package files.reference

import utils.*
import Bundle.*, Tags.*

object Maps extends ReferencePage {

  override def pageSettings =
    super.pageSettings.withTitle("Maps")

  override def blogSettings =
    super.blogSettings.withSections(firstSection)

  val firstSection = Section(
    s"Maps",
    div(
      s"""        
        `Map[String, T]` work as you expect. 
        
        Note that you need a `JsonRW[T]` given instance.
        """.md,
      chl.scala(s"""
        Map(
            "a" -> 5,
            "b" -> 123
        ).toJson
        // {"a":5,"b":123}
        """),
      chl.scala(s"""
        ${tq} {"a":5,"b":123} ${tq}.parseJson[Map[String, Int]]
        // Map(a -> 5, b -> 123)
        """),
      s"""
      Maps are limited in the sense that the values have to be of same type.  
      If you need them different, consider using [case classes](${CaseClasses.ref}) or [dynamic JSON values](${DynamicJson.ref}).
      """.md
    )
  )
}
