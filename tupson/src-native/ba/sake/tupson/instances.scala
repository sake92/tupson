package ba.sake.tupson

import java.net.{URI, URL}

given JsonRW[URL] = JsonRW.stringValue("URL")(value => new URI(value).toURL())
