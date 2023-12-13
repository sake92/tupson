package ba.sake.tupson.config

import com.typesafe.config.*
import org.typelevel.jawn.ast.*
import ba.sake.tupson.*

extension (config: Config) {
  def parse[T: JsonRW]() =
    ConfigUtils.parse(config)
}

private[tupson] object ConfigUtils {

  def parse[T](config: Config)(using rw: JsonRW[T]) =
    val configJsonString = config
      .root()
      .render(
        ConfigRenderOptions.concise().setJson(true)
      )
    val jValue = JParser.parseUnsafe(configJsonString)
    adapt(jValue).toString.parseJson[T]

// if you set a sys/env property,
// the config cannot MAGICALLY know if it is a number or a string, so default is string, wack
// so we adapt string to numbers if possible
  private def adapt(jvalue: JValue): JValue = jvalue match
    case JString(s) =>
      s.toLongOption match
        case Some(n) => JNum(n)
        case None =>
          s.toDoubleOption match
            case Some(d) => JNum(d)
            case None    => jvalue
    case JArray(vs) => JArray(vs.map(adapt))
    case JObject(vs) =>
      val adaptedMap = vs.map { (k, v) => k -> adapt(v) }
      JObject(adaptedMap)
    case _ => jvalue
}
