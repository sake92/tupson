package ba.sake.tupson

import java.net.*

class JvmParseSuite extends munit.FunSuite {

  test("parse URI") {
    intercept[URISyntaxException] {
      """ "/?cmd=200&json={port:1,state:1}" """.parseJson[URI]
    }
    assertEquals(""" "file:/sdfdsfsdf" """.parseJson[URI], URI.create("file:/sdfdsfsdf"))
  }

  test("parse URL") {
    intercept[URISyntaxException] {
      """ "/?cmd=200&json={port:1,state:1}" """.parseJson[URL]
    }
    assertEquals(""" "file:/sdfdsfsdf" """.parseJson[URL], URL("file:/sdfdsfsdf"))
  }

}
