import ba.sake.tupson.*

@main def backwards: Unit = {

  val oldConfig = OldConfig("http://example.com")
  val oldConfigJson = oldConfig.toJson
  println(oldConfigJson)

  val newConfig1 = oldConfigJson.parseJson[NewConfig1]
  println(newConfig1)

  val newConfig2 = oldConfigJson.parseJson[NewConfig2]
  println(newConfig2)
}

case class OldConfig(url: String)

case class NewConfig1(url: String, port: Option[Int])

case class NewConfig2(url: String, port: Int = 1234)
