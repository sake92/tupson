package ba.sake.tupson

import scala.quoted.*

object JsonWriterMacro {

  inline def deriveJsonWriter[T]: JsonWriter[T] =
    ${ deriveJsonWriterImpl[T] }

  private def deriveJsonWriterImpl[T](using
      quotes: Quotes,
      tpe: Type[T]
  ): Expr[JsonWriter[T]] = {
    import quotes.reflect.*
    val tpeTree = TypeTree.of[T]
    val tpeSym = tpeTree.symbol
    if tpeSym.flags.is(Flags.Case) then deriveForCaseClass[T]
    else if tpeSym.flags.is(Flags.Trait & Flags.Sealed) then
      deriveForSealedTrait[T]
    else
      report.throwError(
        s"Did not find `given JsonWriter[${tpeTree.show}]` and macro does not know how to make one automatically."
      )
  }

  private def deriveForCaseClass[T](using
      quotes: Quotes,
      tpe: Type[T]
  ): Expr[JsonWriter[T]] = {
    import quotes.reflect.*

    def writeField(caseClassTerm: Term, field: Symbol): Expr[String] = {
      val fieldValDef = field.tree.asInstanceOf[ValDef]
      val fieldTpe = fieldValDef.tpt.tpe

      // resolve field name
      val jsonPropertyMaybe = field.annotations.find(
        _.tpe.typeSymbol.fullName == "ba.sake.tupson.JsonProperty"
      )
      val fieldName = jsonPropertyMaybe match
        case None => fieldValDef.name
        case Some(jsonPropann) =>
          val Apply(_, args) = jsonPropann
          args.head.asExprOf[String].valueOrAbort

      // resolve typeclass
      val typeClass = lookupJsonWriterFor(fieldTpe) // JsonWriter[$fieldTpe]
      val fieldValue = Select(caseClassTerm, field) // v.field
      val strRepr: Expr[String] =
        applyJsonWriter(typeClass, fieldValue).asExprOf[String]
      '{
        s""""${${ Expr(fieldName) }}": ${${ strRepr }}"""
      }
    }

    def writeBody(valueExpr: Expr[T]): Expr[String] = {
      val typeTree = TypeTree.of[T]

      val parents = TypeRepr.of[T].baseClasses
      val isInSealedHierarchy = parents.exists(_.flags.is(Flags.Sealed))
      val typeMaybe = Option.when(isInSealedHierarchy)(
        Expr(s""""@type":"${typeTree.symbol.fullName}"""")
      )

      val fieldSymbols = typeTree.symbol.caseFields
      val vTerm: Term = valueExpr.asTerm
      val valuesExprs: List[Expr[String]] =
        fieldSymbols.map(writeField(vTerm, _)).prependedAll(typeMaybe)
      val exprOfList: Expr[List[String]] = Expr.ofList(valuesExprs)
      '{ $exprOfList.mkString("{", ", ", "}") }
    }

    '{
      new JsonWriter[T] {
        override def write(value: T): String =
          ${ writeBody('value) }
      }
    }
  }

  def deriveForSealedTrait[T](using
      quotes: Quotes,
      tpe: Type[T]
  ): Expr[JsonWriter[T]] = {
    import quotes.reflect.*

    def writeBody(t: Expr[T]): Expr[String] = {
      val selector: Term = t.asTerm
      val children = TypeTree.of[T].symbol.children
      val ifBranches: List[(Term, Term)] = children.map { sym =>
        val childTpe: TypeTree = TypeIdent(sym)
        val condition: Term =
          TypeApply(Select.unique(selector, "isInstanceOf"), childTpe :: Nil)
        val action: Term =
          applyJsonWriter(
            lookupJsonWriterFor(childTpe.tpe),
            Select.unique(selector, "asInstanceOf").appliedToType(childTpe.tpe)
          )
        condition -> action
      }
      mkIfStatement(ifBranches).asExprOf[String]
    }

    '{
      new JsonWriter[T] {
        override def write(t: T): String = ${ writeBody('t) }
      }
    }
  }

  /** Look up the JsonWriter[$t] typeclass for a given type t */
  private def lookupJsonWriterFor(using
      quotes: Quotes
  )(t: quotes.reflect.TypeRepr): quotes.reflect.Term = {
    import quotes.reflect.*
    val typeClassTpe = TypeRepr.of[JsonWriter].appliedTo(t)
    Implicits.search(typeClassTpe) match
      case res: ImplicitSearchSuccess => res.tree
      case _: ImplicitSearchFailure =>
        report.throwError(s"Could not find an implicit JsonWriter[${t.show}]")
  }

  private def applyJsonWriter(using quotes: Quotes)(
      typeClass: quotes.reflect.Term,
      arg: quotes.reflect.Term
  ): quotes.reflect.Term =
    import quotes.reflect.*
    Apply(Select.unique(typeClass, "write"), List(arg))

  /** Takes a list of branches of the form (condition, action). From that,
    * composes an if statement of the form: if $condition1 then $action1 else if
    * $condition2 then $action2 ... else throw RuntimeException("Unhandled
    * condition encountered during Show derivation")
    */
  private def mkIfStatement(using quotes: Quotes)(
      branches: List[(quotes.reflect.Term, quotes.reflect.Term)]
  ): quotes.reflect.Term = {
    import quotes.reflect.*
    branches match
      case (p1, a1) :: xs => If(p1, a1, mkIfStatement(xs))
      case Nil =>
        ('{
          throw RuntimeException(
            "Unhandled condition encountered during JsonWriter derivation"
          )
        }).asTerm
  }

}
