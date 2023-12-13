package files.tutorials

import utils.*
import Bundle.*, Tags.*

object Writing extends TutorialPage {

  override def pageSettings = super.pageSettings
    .withTitle("Writing")
    .withLabel("Writing")

  override def blogSettings =
    super.blogSettings.withSections(firstSection)

  val firstSection = Section(
    "Writing",
    div(
      s"""
      Writing is really simple.  
      Just call `.toJson` on your data:
      """.md,
      chl.scala(s"""
      import ba.sake.tupson.{given, *}
      
      case class WriteData(
        bln: Boolean,
        int: Int,
        dbl: Double,
        str: String,
        list: Seq[String]
      ) derives JsonRW
    
      val data = WriteData(true, 5, 3.14, "xyz", Seq("a", "b"))

      data.toJson
      // {"str":"xyz","bln":true,"list":["a","b"],"int":5,"dbl":3.14}
      """)
    )
  )
}
