package ba.sake.tupson

import java.net.{URI, URL}
import java.util.{Currency, Locale}

given JsonRW[URL] = JsonRW.stringValue("URL")(value => new URI(value).toURL())
given JsonRW[Currency] = JsonRW.stringValue("Currency")(Currency.getInstance)
given JsonRW[Locale] = JsonRW.stringValue[Locale]("Locale", Locale.forLanguageTag, _.toLanguageTag)
