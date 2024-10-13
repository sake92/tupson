import ba.sake.tupson.{*, given}

@main def backwards: Unit = {

  val oldConfig = ConfigV1("http://example.com")
  val oldConfigJson = oldConfig.toJson
  println(oldConfigJson)

  val newConfig1 = oldConfigJson.parseJson[ConfigV2]
  println(newConfig1.toJson)

  val newConfig2 = oldConfigJson.parseJson[ConfigV3]
  println(newConfig2.toJson)
}

case class ConfigV1(url: String) derives JsonRW

case class ConfigV2(url: String, port: Option[Int]) derives JsonRW

case class ConfigV3(url: String, port: Int = 1234) derives JsonRW
