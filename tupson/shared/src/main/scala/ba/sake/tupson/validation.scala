package ba.sake.validation

case class FieldValidationError(
    path: String,
    fieldValue: Any,
    msg: String
) {
  def withPath(p: String) = copy(path = p)
}

class FieldsValidationException(val errors: Seq[FieldValidationError]) extends RuntimeException(errors.mkString("; "))

//////////
case class FieldChecks[T](text: sourcecode.Text[T], checks: Seq[Check[T]] = Seq.empty[Check[T]]) {
  def is(p: T => Boolean, msg: String): FieldChecks[T] =
    copy(checks = checks.appended(Check(p, msg)))

  def validate: Seq[FieldValidationError] =
    checks.flatMap { case Check(p, msg) =>
      Option.when(!p(text.value)) {
        FieldValidationError(text.source, text.value, msg)
      }
    }

}
case class Check[T](p: T => Boolean, msg: String)

def check[T](text: sourcecode.Text[T]): FieldChecks[T] = FieldChecks(text)

def validate(checks: FieldChecks[?]*): Unit = {
  val errors = checks.flatMap(_.validate)
  if errors.nonEmpty then throw new FieldsValidationException(errors)
}
