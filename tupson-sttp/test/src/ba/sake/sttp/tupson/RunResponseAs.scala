package ba.sake.sttp.tupson

import _root_.sttp.client4.{MappedResponseAs, ResponseAs, ResponseAsByteArray}
import _root_.java.nio.charset.StandardCharsets
import _root_.sttp.model.{ResponseMetadata, StatusCode}

object RunResponseAs {
  def apply[A](responseAs: ResponseAs[A], statusCode: StatusCode = StatusCode.Ok): String => A =
    responseAs.delegate match {
      case ra: MappedResponseAs[_, A, Nothing] @unchecked =>
        ra.raw match {
          case ResponseAsByteArray =>
            s => ra.g(s.getBytes(StandardCharsets.UTF_8), ResponseMetadata(statusCode, "", Nil))
          case _ => sys.error("MappedResponseAs does not wrap a ResponseAsByteArray")
        }
      case _ => sys.error("ResponseAs is not a MappedResponseAs")
    }
}
