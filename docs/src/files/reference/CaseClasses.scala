package files.reference

import utils.*
import Bundle.*, Tags.*

object CaseClasses extends ReferencePage {

  override def pageSettings =
    super.pageSettings.withTitle("Case classes")

  override def blogSettings =
    super.blogSettings.withSections(firstSection)

  val firstSection = Section(
    s"Case classes",
    div(
      s"""
      Case classes are (de)serialized as JSON objects:
      """.md,
      chl.scala("""
      import ba.sake.tupson.{given, *}

      case class Address(street: String) derives JsonRW
      case class Person(name: String, age: Int, address: Address) derives JsonRW

      val person = Person("Meho", 33, Address("Sebilj"))
      
      println(person.toJson)
      // { "age":33, "name":"Meho", "address": { "street":"Sebilj" } }
      """)
    )
  )
}
