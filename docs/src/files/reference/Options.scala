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
    s"""        
    `Option[T]` work as you expect.  
    `None` corresponds to JSON's `null`.  
    
    Note that you need a `JsonRW[T]` given instance.
    """.md
  )
}
