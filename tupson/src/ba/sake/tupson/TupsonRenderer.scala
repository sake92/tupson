package ba.sake.tupson

import org.typelevel.jawn.ast.*

private[tupson] object TupsonRenderer {

  def render(jValue: JValue): String =
    FastRenderer.render(jValue)

  def render(jValue: JValue, pretty: Boolean, prettySpaces: Int, sort: Boolean): String = {
    if !pretty then
      if sort then CanonicalRenderer.render(jValue)
      else FastRenderer.render(jValue)
    else {
      if prettySpaces <= 0 then
        throw IllegalArgumentException("prettySpaces must be positive")
      val sb = new StringBuilder
      renderPretty(jValue, sb, 0, prettySpaces, sort)
      sb.toString
    }
  }

  private def renderPretty(
      jValue: JValue,
      sb: StringBuilder,
      depth: Int,
      prettySpaces: Int,
      sort: Boolean
  ): Unit = jValue match {
    case JObject(fields) =>
      val orderedFields =
        if sort then fields.toSeq.sortBy(_._1)
        else fields.toSeq
      if orderedFields.isEmpty then sb.append("{}")
      else {
        sb.append("{\n")
        orderedFields.zipWithIndex.foreach { case ((key, value), index) =>
          indent(sb, depth + 1, prettySpaces)
          sb.append(FastRenderer.render(JString(key)))
          sb.append(": ")
          renderPretty(value, sb, depth + 1, prettySpaces, sort)
          if index < orderedFields.size - 1 then sb.append(',')
          sb.append('\n')
        }
        indent(sb, depth, prettySpaces)
        sb.append('}')
      }
    case JArray(values) =>
      if values.isEmpty then sb.append("[]")
      else {
        sb.append("[\n")
        values.zipWithIndex.foreach { case (value, index) =>
          indent(sb, depth + 1, prettySpaces)
          renderPretty(value, sb, depth + 1, prettySpaces, sort)
          if index < values.length - 1 then sb.append(',')
          sb.append('\n')
        }
        indent(sb, depth, prettySpaces)
        sb.append(']')
      }
    case other =>
      sb.append(FastRenderer.render(other))
  }

  private def indent(sb: StringBuilder, depth: Int, prettySpaces: Int): Unit =
    sb.append(" " * (depth * prettySpaces))
}
