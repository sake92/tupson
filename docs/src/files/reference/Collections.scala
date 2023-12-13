package files.reference

import utils.*
import Bundle.*, Tags.*

object Collections extends ReferencePage {

  override def pageSettings =
    super.pageSettings.withTitle("Collections")

  override def blogSettings =
    super.blogSettings.withSections(firstSection)

  val firstSection = Section(
    s"Seqs",
    s"""        
    `Seq[T]`, `List[T]`, `Set[T]`, `Array[T]` are supported.  
    
    Note that you need a `JsonRW[T]` given instance.
    """.md
  )
}
