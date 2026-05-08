package ba.sake.tupson

import org.typelevel.jawn.ast.*

private[tupson] object TupsonRenderer {

  def render(jValue: JValue, spaces: Int, sort: Boolean): String = {
    val normalizedSpaces =
      if spaces <= 0 then 0 else spaces
    if normalizedSpaces == 0 then
      if sort then CanonicalRenderer.render(jValue)
      else FastRenderer.render(jValue)
    else {
      val sb = new StringBuilder
      renderPretty(jValue, sb, 0, normalizedSpaces, sort)
      sb.toString
    }
  }

  private def renderPretty(
      jValue: JValue,
      sb: StringBuilder,
      depth: Int,
      spaces: Int,
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
          indent(sb, depth + 1, spaces)
          sb.append(FastRenderer.render(JString(key)))
          sb.append(": ")
          renderPretty(value, sb, depth + 1, spaces, sort)
          if index < orderedFields.size - 1 then sb.append(',')
          sb.append('\n')
        }
        indent(sb, depth, spaces)
        sb.append('}')
      }
    case JArray(values) =>
      if values.isEmpty then sb.append("[]")
      else {
        sb.append("[\n")
        values.zipWithIndex.foreach { case (value, index) =>
          indent(sb, depth + 1, spaces)
          renderPretty(value, sb, depth + 1, spaces, sort)
          if index < values.length - 1 then sb.append(',')
          sb.append('\n')
        }
        indent(sb, depth, spaces)
        sb.append(']')
      }
    case other =>
      sb.append(FastRenderer.render(other))
  }

  private def indent(sb: StringBuilder, depth: Int, spaces: Int): Unit =
    sb.append(" " * (depth * spaces))
}
