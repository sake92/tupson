import ba.sake.tupson.*

@main def rename: Unit = {
  var x = CC(1, "xyz", 2)
  println(x.toJson)
}

case class CC(
    @named("x") i: Int,

    s: String,

    // this would throw exception:
    // @named("x")
    j: Int
) derives JsonRW
