package files.reference

import utils.*
import Bundle.*, Tags.*

object SimpleEnums extends ReferencePage {

  override def pageSettings =
    super.pageSettings.withTitle("Simple enums")

  override def blogSettings =
    super.blogSettings.withSections(firstSection)

  val firstSection = Section(
    s"Simple enums",
    div(
      s"""
      Simple enums are (de)serialized as JSON strings.  
      By "simple" we mean an enum that only has "singleton cases" as defined in the [docs](https://docs.scala-lang.org/scala3/reference/enums/desugarEnums.html).  
      That is, enum who's `case`s don't have a parameter clause.
      """.md,
      chl.scala("""
      enum Semaphore derives JsonRW:
        case Red, Yellow, Green

      val semaphore = Semaphore.Red
      println(semaphore.toJson)
      // "Red"
      """)
    )
  )
}
