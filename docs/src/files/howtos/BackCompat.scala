package files.howtos

import utils.*
import Bundle.*, Tags.*

object BackCompat extends HowToPage {

  override def pageSettings =
    super.pageSettings.withTitle("Backwards compatibility")

  override def blogSettings =
    super.blogSettings.withSections(firstSection)

  val firstSection = Section(
    s"Backwards compatibility",
    s"""
      Let's say you have a `case class MyConfig(url: String)`.  
      Now you need to add another property: `port: Int`, but **without breaking existing serialized values**.

      You have 2 options:
      1. use an `Option[Int]`, and set a default value later if it is missing
      2. use a `Int = MyDefaultValue` to avoid `Option` gymnastics
      """.md,
    List(
      Section(
        "Using Option",
        div(
          s"""
          Using `Option[T]` is handy when you don't have a default value.  
          But it is a bit cumbersome to handle:
          """.md,
          chl.scala(s"""
          case class MyConfig(url: String, port: Option[Int]) derives JsonRW
          parsedConfig.port.getOrElse(...)
          """)
        )
      ),
      Section(
        "Using default value",
        div(
          s"""
          If you do have a default value for a particular property, by all means do use it.  
          It will make your life much easier, you can pretend it was always there:
          """.md,
          chl.scala(s"""
          case class MyConfig(url: String, port: Int = 1234) derives JsonRW
          """)
        )
      )
    )
  )
}
