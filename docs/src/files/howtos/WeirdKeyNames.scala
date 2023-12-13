package files.howtos

import utils.*
import Bundle.*, Tags.*

object WeirdKeyNames extends HowToPage {

  override def pageSettings =
    super.pageSettings.withTitle("Weird key names")

  override def blogSettings =
    super.blogSettings.withSections(firstSection)

  val firstSection = Section(
    s"Weird, unusual key names",
    div(
      s"""
      You can use the Scala's "backticks" language feature to use weird names for keys:
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
