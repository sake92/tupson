package ba.sake.tupson

// java.net
// there is no RW for InetAddress because it could do host lookups.. :/
given JsonRW[URI] = new {
  override def write(value: URI): JValue = JString(value.toString())
  override def parse(path: String, jValue: JValue): URI = jValue match
    case JString(s) => new URI(s)
    case other      => JsonRW.typeMismatchError(path, "URI", other)
}

given JsonRW[URL] = new {
  override def write(value: URL): JValue = JString(value.toString())
  override def parse(path: String, jValue: JValue): URL = jValue match
    case JString(s) => new URI(s).toURL()
    case other      => JsonRW.typeMismatchError(path, "URL", other)
}
