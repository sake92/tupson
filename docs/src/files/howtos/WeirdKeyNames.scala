package files.howtos

import utils.*
import Bundle.*, Tags.*

object WeirdKeyNames extends HowToPage {

  override def pageSettings =
    super.pageSettings.withTitle("Use weird key names")

  override def blogSettings =
    super.blogSettings.withSections(firstSection)

  val firstSection = Section(
    s"How to use weird key names?",
    div(
      s"""
      Sometimes you need spaces or other characters in your JSON keys.  
      You can use Scala's "backticks" language feature for that:
      """.md,
      chl.scala("""
      case class Address(`street no`: String) derives JsonRW

      val address = Address("My Street 123")
      
      println(address.toJson)
      // {"street no":"My Street 123"}
      """)
    )
  )
}
