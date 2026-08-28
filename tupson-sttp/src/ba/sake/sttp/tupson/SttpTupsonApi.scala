package ba.sake.sttp.tupson

// NOTE: this sits inside the `ba.sake.sttp` tree, whose `sttp` segment shadows the
// root `sttp` package, so sttp types must be imported via the `_root_.` prefix here.
import ba.sake.tupson.{*, given}
import _root_.sttp.client4.*
import _root_.sttp.client4.ResponseAs.deserializeEitherWithErrorOrThrow
import _root_.sttp.client4.ResponseException.DeserializationException
import _root_.sttp.client4.ResponseException.UnexpectedStatusCode
import _root_.sttp.client4.json.*
import _root_.sttp.model.MediaType

trait SttpTupsonApi {

  /** Serialize the given value as JSON, to be used as a request's body using `sttp.client4.Request.body`. */
  def asJson[B](b: B)(using rw: JsonRW[B]): StringBody =
    StringBody(b.toJson(0), "UTF-8", MediaType.ApplicationJson)

  /** If the response is successful (2xx), tries to deserialize the body from a string into JSON. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(UnexpectedStatusCode(String))` if the response code was other than 2xx (deserialization is not
    *     attempted)
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJson[B: JsonRW: IsOption]: ResponseAs[Either[ResponseException[String], B]] =
    asString.mapWithMetadata(ResponseAs.deserializeRightWithError(deserializeJson)).showAsJson

  def deserializeJson[B: JsonRW: IsOption]: String => Either[TupsonException, B] =
    (s: String) =>
      // mirror sttp's (private) JsonInput.sanitize: an empty body parses as `null` for Option types
      val body = if summon[IsOption[B]].isOption && s.trim.isEmpty then "null" else s
      try Right(body.parseJson[B])
      catch { case e: TupsonException => Left(e) }

  /** If the response is successful (2xx), tries to deserialize the body from a string into JSON. Otherwise, if the
    * response code is other than 2xx, or a deserialization error occurs, throws an [[ResponseException]] / returns a
    * failed effect.
    */
  def asJsonOrFail[B: JsonRW: IsOption]: ResponseAs[B] = asJson[B].orFail.showAsJsonOrFail

  /** Tries to deserialize the body from a string into JSON, regardless of the response code. Returns:
    *   - `Right(b)` if the parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonAlways[B: JsonRW: IsOption]: ResponseAs[Either[DeserializationException, B]] =
    asStringAlways.mapWithMetadata(ResponseAs.deserializeWithError(deserializeJson)).showAsJsonAlways

  /** Tries to deserialize the body from a string into JSON, using different deserializers depending on the status
    * code. Returns:
    *   - `Right(B)` if the response was 2xx and parsing was successful
    *   - `Left(UnexpectedStatusCode(E))` if the response was other than 2xx and parsing was successful
    *   - `Left(DeserializationException)` if there's an error during deserialization
    */
  def asJsonEither[E: JsonRW: IsOption, B: JsonRW: IsOption]: ResponseAs[Either[ResponseException[E], B]] =
    asJson[B].mapLeft { (l: ResponseException[String]) =>
      l match {
        case UnexpectedStatusCode(e, meta) =>
          deserializeJson[E].apply(e).fold(DeserializationException(e, _, meta), UnexpectedStatusCode(_, meta))
        case de @ DeserializationException(_, _, _) => de
      }
    }.showAsJsonEither

  /** Deserializes the body from a string into JSON, using different deserializers depending on the status code. If a
    * deserialization error occurs, throws a [[DeserializationException]] / returns a failed effect.
    */
  def asJsonEitherOrFail[E: JsonRW: IsOption, B: JsonRW: IsOption]: ResponseAs[Either[E, B]] =
    asStringAlways
      .mapWithMetadata(deserializeEitherWithErrorOrThrow(deserializeJson[E], deserializeJson[B]))
      .showAsJsonEitherOrFail
}
