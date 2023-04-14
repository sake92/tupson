package ba.sake.validation

case class FieldValidationError(
    path: String,
    fieldName: String,
    fieldValue: Any,
    msg: String
)

class FieldValidationException(error: FieldValidationError)
    extends RuntimeException(error.toString)

class FieldsValidationException(errors: Seq[FieldValidationError])
    extends RuntimeException(errors.mkString("; "))

def check[T](
    text: sourcecode.Text[T],
    p: T => Boolean,
    msg: String
): Option[FieldValidationError] = {
  Option.when(!p(text.value)) {
    FieldValidationError("", text.source, text.value, msg)
  }
}

def validate[T](validationErrors: Option[FieldValidationError]*): Unit = {
  val errors = validationErrors.flatten
  if errors.nonEmpty then throw new FieldsValidationException(errors)
}
