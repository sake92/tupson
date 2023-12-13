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
    s"""        
    `Map[String, T]` work as you expect. 
    
    Note that you need a `JsonRW[T]` given instance.
    """.md
  )
}
