package ba.sake.tupson

import java.net.*
import java.util.*

class JvmParseSuite extends munit.FunSuite {

  test("parse URI strictness (JVM)") {
    intercept[URISyntaxException] {
      """ "/?cmd=200&json={port:1,state:1}" """.parseJson[URI]
    }
  }

  test("parse URL") {
    intercept[URISyntaxException] {
      """ "/?cmd=200&json={port:1,state:1}" """.parseJson[URL]
    }
    assertEquals(""" "file:/sdfdsfsdf" """.parseJson[URL], URL("file:/sdfdsfsdf"))
  }

  test("Currency roundtrip") {
    assertEquals(Currency.getInstance("EUR").toJson(spaces = 0), "\"EUR\"")
    assertEquals("\"EUR\"".parseJson[Currency], Currency.getInstance("EUR"))
  }

  test("Currency unknown code propagates IllegalArgumentException") {
    intercept[IllegalArgumentException] {
      "\"XXXZZZ\"".parseJson[Currency]
    }
  }

  test("Locale roundtrip") {
    assertEquals(Locale.US.toJson(spaces = 0), "\"en-US\"")
    assertEquals("\"en-US\"".parseJson[Locale], Locale.US)
    assertEquals("\"sr-BA\"".parseJson[Locale], Locale.forLanguageTag("sr-BA"))
  }

}
