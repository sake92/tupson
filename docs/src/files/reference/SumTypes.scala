package files.reference

import utils.*
import Bundle.*, Tags.*

object SumTypes extends ReferencePage {

  override def pageSettings =
    super.pageSettings.withTitle("Sum types")

  override def blogSettings =
    super.blogSettings.withSections(firstSection)

  val firstSection = Section(
    s"Sum types",
    div(
      s"""
      Sum types are `sealed trait`s, `sealed class`es and
        [non-singleton](https://docs.scala-lang.org/scala3/reference/enums/desugarEnums.html) `enum`s.  
      Since they have one or more subtypes, we need to disambiguate between them somehow.  

      The default in ${Consts.ProjectName} is to use the `@type` property.  
      Its value is the *simple type name* of class or enum case.  
      This makes JSON independent of scala/java package and it is more readable.

      Example:
      """.md,
      chl.scala("""
      enum Color derives JsonRW:
        case Hex(num: String)
        case Yellow

      val color = Color.Hex("FFF")
      
      println(color.toJson)
      // {"@type":"Hex","num":"FFF"}
      """)
    ),
    List(
      Section(
        s"Custom type key",
        div(
          "You can use some other key by annotating the sum type with `@discriminator`:".md,
          chl.scala("""
          @discriminator("myOtherKey")
          enum Color derives JsonRW ...
          """)
        )
      )
    )
  )
}
