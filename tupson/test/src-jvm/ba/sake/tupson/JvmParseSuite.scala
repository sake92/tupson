package ba.sake.tupson

import java.net.*

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

}
