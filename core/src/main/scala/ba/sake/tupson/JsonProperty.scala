package ba.sake.tupson

import scala.annotation.StaticAnnotation
import scala.annotation.meta.field

// https://www.scala-lang.org/api/2.13.3/scala/annotation/meta/index.html
// - when annotating case class field, it would only be on the *parameter*, not on the *field* itself
// - adding @field annotation also adds this annotation to the field

@field
final class JsonProperty(val name: String) extends StaticAnnotation
