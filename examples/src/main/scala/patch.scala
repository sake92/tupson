import ba.sake.tupson.{*, given}

case class UserPatch(name: Patch[String], address: Patch[Option[String]]) derives JsonRW

@main def patchExample: Unit =
  val userPatch = """{ "name": "Ada", "address": null }""".parseJson[UserPatch]
  println(userPatch) // UserPatch(Set(Ada), Set(None))

  val nameOnly = UserPatch(Patch.Set("Ada"), Patch.Keep)
  println(nameOnly.toJson(spaces = 0)) // {"name":"Ada"}

  val noChanges = UserPatch(Patch.Keep, Patch.Keep)
  println(noChanges.toJson(spaces = 0)) // {}
