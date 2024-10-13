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
    div(
      s"""        
        `Seq[T]`, `List[T]`, `Set[T]`, `Array[T]` are supported.  
        
        Note that you need a `JsonRW[T]` given instance.
        """.md,
      chl.scala(s"""
        Seq.empty[String].toJson
        // []

        Seq("a", "b").toJson
        // ["a","b"]
        """),
      chl.scala(s"""
        ${tq} [] ${tq}.parseJson[Seq[String]]
        // Seq()

        ${tq} ["a","b"] ${tq}.parseJson[Seq[String]]
        // Seq(a,b)
        """)
    )
  )
}
