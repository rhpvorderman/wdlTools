package wdlTools.syntax

import AbstractSyntax._

object Util {

  /**
    * Utility function for writing an expression in a human readable form
    * @param expr the expression to convert
    * @param callback a function that optionally converts expression to string - if it returns
    *                 Some(string), string is returned rather than the default formatting. This
    *                 can be used to provide custom formatting for some or all parts of a nested
    *                 expression.
    */
  def exprToString(expr: Expr, callback: Option[Expr => Option[String]] = None): String = {
    if (callback.isDefined) {
      val s = callback.get(expr)
      if (s.isDefined) {
        return s.get
      }
    }

    expr match {
      case ValueNone(_)                    => "None"
      case ValueString(value, _)           => value
      case ValueBoolean(value: Boolean, _) => value.toString
      case ValueInt(value, _)              => value.toString
      case ValueFloat(value, _)            => value.toString
      case ExprIdentifier(id: String, _)   => id

      case ExprCompoundString(value: Vector[Expr], _) =>
        val vec = value.map(x => exprToString(x, callback)).mkString(", ")
        s"ExprCompoundString(${vec})"
      case ExprPair(l, r, _) => s"(${exprToString(l, callback)}, ${exprToString(r, callback)})"
      case ExprArray(value: Vector[Expr], _) =>
        "[" + value.map(x => exprToString(x, callback)).mkString(", ") + "]"
      case ExprMember(key, value, _) =>
        s"${exprToString(key, callback)} : ${exprToString(value, callback)}"
      case ExprMap(value: Vector[ExprMember], _) =>
        val m = value
          .map(x => exprToString(x, callback))
          .mkString(", ")
        "{ " + m + " }"
      case ExprObject(value: Vector[ExprMember], _) =>
        val m = value
          .map(x => exprToString(x, callback))
          .mkString(", ")
        s"object($m)"
      // ~{true="--yes" false="--no" boolean_value}
      case ExprPlaceholderEqual(t: Expr, f: Expr, value: Expr, _) =>
        s"{true=${exprToString(t, callback)} false=${exprToString(f, callback)} ${exprToString(value, callback)}"

      // ~{default="foo" optional_value}
      case ExprPlaceholderDefault(default: Expr, value: Expr, _) =>
        s"{default=${exprToString(default, callback)} ${exprToString(value, callback)}}"

      // ~{sep=", " array_value}
      case ExprPlaceholderSep(sep: Expr, value: Expr, _) =>
        s"{sep=${exprToString(sep, callback)} ${exprToString(value, callback)}"

      // operators on one argument
      case ExprUnaryPlus(value: Expr, _) =>
        s"+ ${exprToString(value, callback)}"
      case ExprUnaryMinus(value: Expr, _) =>
        s"- ${exprToString(value, callback)}"
      case ExprNegate(value: Expr, _) =>
        s"not(${exprToString(value, callback)})"

      // operators on two arguments
      case ExprLor(a: Expr, b: Expr, _) =>
        s"${exprToString(a, callback)} || ${exprToString(b, callback)}"
      case ExprLand(a: Expr, b: Expr, _) =>
        s"${exprToString(a, callback)} && ${exprToString(b, callback)}"
      case ExprEqeq(a: Expr, b: Expr, _) =>
        s"${exprToString(a, callback)} == ${exprToString(b, callback)}"
      case ExprLt(a: Expr, b: Expr, _) =>
        s"${exprToString(a, callback)} < ${exprToString(b, callback)}"
      case ExprGte(a: Expr, b: Expr, _) =>
        s"${exprToString(a, callback)} >= ${exprToString(b, callback)}"
      case ExprNeq(a: Expr, b: Expr, _) =>
        s"${exprToString(a, callback)} != ${exprToString(b, callback)}"
      case ExprLte(a: Expr, b: Expr, _) =>
        s"${exprToString(a, callback)} <= ${exprToString(b, callback)}"
      case ExprGt(a: Expr, b: Expr, _) =>
        s"${exprToString(a, callback)} > ${exprToString(b, callback)}"
      case ExprAdd(a: Expr, b: Expr, _) =>
        s"${exprToString(a, callback)} + ${exprToString(b, callback)}"
      case ExprSub(a: Expr, b: Expr, _) =>
        s"${exprToString(a, callback)} - ${exprToString(b, callback)}"
      case ExprMod(a: Expr, b: Expr, _) =>
        s"${exprToString(a, callback)} % ${exprToString(b, callback)}"
      case ExprMul(a: Expr, b: Expr, _) =>
        s"${exprToString(a, callback)} * ${exprToString(b, callback)}"
      case ExprDivide(a: Expr, b: Expr, _) =>
        s"${exprToString(a, callback)} / ${exprToString(b, callback)}"

      // Access an array element at [index]
      case ExprAt(array: Expr, index: Expr, _) =>
        s"${exprToString(array, callback)}[${index}]"

      // conditional:
      // if (x == 1) then "Sunday" else "Weekday"
      case ExprIfThenElse(cond: Expr, tBranch: Expr, fBranch: Expr, _) =>
        s"if (${exprToString(cond, callback)}) then ${exprToString(tBranch, callback)} else ${exprToString(fBranch, callback)}"

      // Apply a standard library function to arguments. For example:
      //   read_int("4")
      case ExprApply(funcName: String, elements: Vector[Expr], _) =>
        val args = elements.map(x => exprToString(x, callback)).mkString(", ")
        s"${funcName}(${args})"

      case ExprGetName(e: Expr, id: String, _) =>
        s"${exprToString(e, callback)}.${id}"
    }
  }

  def metaValueToString(value: MetaValue,
                        callback: Option[MetaValue => Option[String]] = None): String = {
    if (callback.isDefined) {
      val s = callback.get(value)
      if (s.isDefined) {
        return s.get
      }
    }
    value match {
      case MetaValueNull(_)                    => "null"
      case MetaValueString(value, _)           => value
      case MetaValueBoolean(value: Boolean, _) => value.toString
      case MetaValueInt(value, _)              => value.toString
      case MetaValueFloat(value, _)            => value.toString
      case MetaValueArray(value, _) =>
        "[" + value.map(x => metaValueToString(x, callback)).mkString(", ") + "]"
      case MetaValueObject(value, _) =>
        val m = value
          .map(x => s"${x.id} : ${metaValueToString(x.value, callback)}")
          .mkString(", ")
        s"{$m}"
    }
  }
}
