package ba.sake.sttp.tupson

import ba.sake.tupson.{*, given}
import _root_.sttp.client4.*
import _root_.sttp.client4.testing.SyncBackendStub
import _root_.sttp.model.StatusCode

class BackendStubSuite extends munit.FunSuite {

  case class Person(name: String) derives JsonRW

  @discriminator("type")
  enum Shape derives JsonRW:
    case Circle(radius: Double)
    case Square(side: Double)

  test("roundtrip via SyncBackendStub") {
    val backend = SyncBackendStub.whenAnyRequest.thenRespondAdjust("""{"name":"John"}""")
    val response = basicRequest
      .post(uri"http://example.org")
      .body(asJson(Person("John")))
      .response(asJson[Person])
      .send(backend)
    assert(response.is200)
    assertEquals(response.body, Right(Person("John")))
  }

  test("sum types work through sttp") {
    val backend = SyncBackendStub.whenAnyRequest.thenRespondAdjust("""{"type":"Circle","radius":2.0}""")
    val response = basicRequest.get(uri"http://example.org").response(asJson[Shape]).send(backend)
    assertEquals(response.body, Right(Shape.Circle(2.0)))
  }

  test("error response is reported as UnexpectedStatusCode") {
    val backend =
      SyncBackendStub.whenAnyRequest.thenRespondAdjust("""{"error":"nope"}""", StatusCode.NotFound)
    val response = basicRequest.get(uri"http://example.org").response(asJson[Person]).send(backend)
    response.body match {
      case Left(ResponseException.UnexpectedStatusCode(body, _)) => assertEquals(body, """{"error":"nope"}""")
      case other                                                 => fail(s"expected UnexpectedStatusCode but got $other")
    }
  }
}
