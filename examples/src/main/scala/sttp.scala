import ba.sake.tupson.{*, given}
import ba.sake.sttp.tupson.*
import sttp.client4.*
import sttp.client4.testing.SyncBackendStub

@main def sttpExample: Unit = {
  // 1) serialize any JsonRW value as a request body
  val request = basicRequest
    .post(uri"http://example.com/api/users")
    .body(asJson(CreateUserRequest("Sake", 30)))
  println(request.toCurl)
  println()

  // 2) parse the response body via the `asJson` response handler
  val backend = SyncBackendStub.whenAnyRequest
    .thenRespondAdjust("""{"id": 1, "name": "Sake", "age": 30}""")
  val response = request.response(asJson[User]).send(backend)
  println(response.body)
  println()

  // 3) `asJsonOrFail` throws (or fails the effect) on non-2xx / parse errors
  val gotUser = basicRequest
    .get(uri"http://example.com/api/users/1")
    .response(asJsonOrFail[User])
    .send(backend)
  println(gotUser.body)
}

case class CreateUserRequest(name: String, age: Int) derives JsonRW

case class User(id: Int, name: String, age: Int) derives JsonRW
