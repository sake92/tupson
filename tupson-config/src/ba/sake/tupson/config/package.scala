package ba.sake.tupson.config

import com.typesafe.config.*
import ba.sake.tupson.{*, given}

extension (config: Config) {
  def parseConfig[T: JsonRW] =
    ConfigUtils.parse(config)
}

private[tupson] object ConfigUtils {

  def parse[T](config: Config)(using rw: JsonRW[T]) =
    val configJsonString = config
      .root()
      .render(
        ConfigRenderOptions.concise().setJson(true)
      )
    val json = configJsonString.parseJson[Json]
    adapt(json).toJson().parseJson[T]

// if you set a sys/env property,
// the config cannot MAGICALLY know if it is a number or a string, so default is string, wack
// so we adapt string to numbers if possible
  private def adapt(json: Json): Json = json match
    case Json.Str(s) =>
      s.toLongOption match
        case Some(n) => Json.Num(n.toString)
        case None    =>
          s.toDoubleOption match
            case Some(d) if d.isFinite => Json.Num(d.toString)
            case _                    => json
    case Json.Arr(vs) => Json.Arr(vs.map(adapt))
    case Json.Obj(vs) =>
      val adaptedMap = vs.map { (k, v) => k -> adapt(v) }
      Json.Obj(adaptedMap)
    case _ => json
}
