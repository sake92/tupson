package ba.sake.tupson

import java.net.*
import java.util.Currency
import java.util.Locale
import org.typelevel.jawn.ast.*

// java.net
// there is no RW for InetAddress because it could do host lookups.. :/
given JsonRW[URL] with {
  override def write(value: URL): JValue = JString(value.toString())
  override def parse(path: String, jValue: JValue): URL = jValue match
    case JString(s) => new URI(s).toURL()
    case other      => JsonRW.typeMismatchError(path, "URL", other)
}

// java.util
// on JS/Native these need an app-specific CLDR data DB (see scala-java-locales),
// so they are JVM-only (JDK has the data built in)
given JsonRW[Currency] with {
  override def write(value: Currency): JValue = JString(value.getCurrencyCode)
  override def parse(path: String, jValue: JValue): Currency = jValue match
    case JString(s) => Currency.getInstance(s)
    case other      => JsonRW.typeMismatchError(path, "Currency", other)
}

given JsonRW[Locale] with {
  override def write(value: Locale): JValue = JString(value.toLanguageTag)
  override def parse(path: String, jValue: JValue): Locale = jValue match
    case JString(s) => Locale.forLanguageTag(s)
    case other      => JsonRW.typeMismatchError(path, "Locale", other)
}
