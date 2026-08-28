package ba.sake.sttp.tupson

import ba.sake.tupson.{*, given}
import _root_.sttp.client4.*
import _root_.sttp.client4.ResponseException.{DeserializationException, UnexpectedStatusCode}
import _root_.sttp.model.{MediaType, StatusCode}

class SttpTupsonApiSuite extends munit.FunSuite {

  case class Person(name: String) derives JsonRW
  case class Outer(foo: Inner, bar: String) derives JsonRW
  case class Inner(a: Int, b: Boolean) derives JsonRW
  case class ApiError(message: String) derives JsonRW

  test("encode request body") {
    val body = asJson(Person("John"))
    assertEquals(body.s, """{"name":"John"}""")
    assertEquals(body.defaultContentType, MediaType.ApplicationJson)
  }

  test("encode request body compact and sorted") {
    val body = asJson(Outer(Inner(42, true), "cats"))
    assertEquals(body.s, """{"bar":"cats","foo":{"a":42,"b":true}}""")
  }

  test("decode response") {
    val responseAs = asJson[Person]
    assertEquals(RunResponseAs(responseAs)("""{"name":"John"}"""), Right(Person("John")))
  }

  test("decode None from empty body") {
    val responseAs = asJson[Option[Person]]
    assertEquals(RunResponseAs(responseAs)(""), Right(None))
  }

  test("fail to decode invalid json") {
    val responseAs = asJson[Person]
    val result = RunResponseAs(responseAs)("""{"name":123}""")
    result match {
      case Left(DeserializationException(body, cause, _)) =>
        assertEquals(body, """{"name":123}""")
        assert(cause.isInstanceOf[ParsingException])
        assert(cause.getMessage.contains("$.name"), cause.getMessage)
      case other => fail(s"expected DeserializationException but got $other")
    }
  }

  test("non-2xx returns UnexpectedStatusCode") {
    val responseAs = asJson[Person]
    val result = RunResponseAs(responseAs, StatusCode.NotFound)("""{"error":"nope"}""")
    result match {
      case Left(UnexpectedStatusCode(body, _)) => assertEquals(body, """{"error":"nope"}""")
      case other                               => fail(s"expected UnexpectedStatusCode but got $other")
    }
  }

  test("asJsonOrFail returns value on 2xx") {
    val responseAs = asJsonOrFail[Person]
    assertEquals(RunResponseAs(responseAs)("""{"name":"John"}"""), Person("John"))
  }

  test("asJsonOrFail throws on non-2xx") {
    val responseAs = asJsonOrFail[Person]
    intercept[ResponseException[?]] {
      RunResponseAs(responseAs, StatusCode.BadRequest)("""{"error":"nope"}""")
    }
  }

  test("asJsonOrFail throws on deserialization error") {
    val responseAs = asJsonOrFail[Person]
    val e = intercept[DeserializationException] {
      RunResponseAs(responseAs)("""{"name":123}""")
    }
    assert(e.cause.isInstanceOf[ParsingException])
    assert(e.cause.getMessage.contains("$.name"), e.cause.getMessage)
  }

  test("asJsonAlways decodes regardless of status") {
    val responseAs = asJsonAlways[Person]
    assertEquals(RunResponseAs(responseAs, StatusCode.BadRequest)("""{"name":"John"}"""), Right(Person("John")))
  }

  test("asJsonEither parses error body on non-2xx") {
    val responseAs = asJsonEither[ApiError, Person]
    val result = RunResponseAs(responseAs, StatusCode.BadRequest)("""{"message":"bad"}""")
    result match {
      case Left(UnexpectedStatusCode(err, _)) => assertEquals(err, ApiError("bad"))
      case other                              => fail(s"expected UnexpectedStatusCode but got $other")
    }
  }

  test("asJsonEither parses success body on 2xx") {
    val responseAs = asJsonEither[ApiError, Person]
    assertEquals(RunResponseAs(responseAs)("""{"name":"John"}"""), Right(Person("John")))
  }

  test("asJsonEitherOrFail returns Left on non-2xx") {
    val responseAs = asJsonEitherOrFail[ApiError, Person]
    assertEquals(RunResponseAs(responseAs, StatusCode.BadRequest)("""{"message":"bad"}"""), Left(ApiError("bad")))
  }

  test("asJsonEitherOrFail throws on deserialization error") {
    val responseAs = asJsonEitherOrFail[ApiError, Person]
    val e = intercept[DeserializationException] {
      RunResponseAs(responseAs, StatusCode.BadRequest)("""{"msg":"bad"}""")
    }
    assert(e.cause.isInstanceOf[ParsingException])
    assert(e.cause.getMessage.contains("$.message"), e.cause.getMessage)
  }
}
