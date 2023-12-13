package files.tutorials

import utils.*
import Bundle.*, Tags.*

object Parsing extends TutorialPage {

  override def pageSettings = super.pageSettings
    .withTitle("Parsing")
    .withLabel("Parsing")

  override def blogSettings =
    super.blogSettings.withSections(firstSection)

  val firstSection = Section(
    "Parsing",
    div(
      s"""
      Let's start with a simple `case class MyData`.  
      Note that you need to derive the `JsonRW` typeclass to get the ${Consts.ProjectName} features.
      """.md,
      chl.scala(s"""
      import ba.sake.tupson.{given, *}

      case class MyData(
          bln: Boolean,
          int: Int,
          s: String
      ) derives JsonRW
      """),
      s"Then, you can call `parseJson[T]` function on a `String` to parse it to the desired type:".md,
      chl.scala(s"""
      val res = ${tq}{ "bln":true, "int":5, "s":"dsds" }${tq}.parseJson[MyData]
      // MyData(true,5,dsds)
      """)
    ),
    List(
      Section(
        "General errors",
        div(
          s"""
          If parsing fails you will get a `TupsonException`:
          """.md,
          chl.scala(s"""
          ${tq}{ "bln":true ${tq}.parseJson[MyData]
          // TupsonException: incomplete JSON
          """)
        )
      ),
      Section(
        "Specific errors",
        div(
          s"""
          ${Consts.ProjectName} will give you the most specific error(s) as possible:
          """.md,
          chl.scala(s"""
          ${tq}{ "bln":123 }${tq} .parseJson[MyData]
          // Key '$$.bln' with value '123' should be Boolean but it is Number; Key '$$.int' is missing; Key '$$.s' is missing
          """)
        )
      ),
      Section(
        "Collecting errors",
        div(
          s"""
          You can catch `ParsingException` to collect the errors.  
          This exception contains `errors: Seq[ParseError]`, list of errors that happened while parsing.  
          Every `ParseError` object contains a [JSONPath](https://www.ietf.org/archive/id/draft-goessner-dispatch-jsonpath-00.html#name-jsonpath-examples) path to the field which has errors in it.  
          You saw some of it in the error stacktrace above.
          """.md,
          chl.scala(s"""
          try {
            ${tq}{ "bln":123 } ${tq} .parseJson[MyData]
          } catch {
            case pe: ParsingException =>
              val errors = pe.errors.mkString("\\n")
              println(s"errors:\\n$${errors}")
          }
          // errors:
          // ParseError($$.bln,should be Boolean but it is Number,Some(123))
          // ParseError($$.int,is missing,None)
          // ParseError($$.s,is missing,None)
          """)
        )
      )
    )
  )
}
