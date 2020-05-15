package wdlTools.linter

import java.net.URL

import wdlTools.linter.Severity.Severity
import wdlTools.syntax.WdlVersion
import wdlTools.types.{TypedAbstractSyntax, TypedSyntaxTreeVisitor, Unification}
import wdlTools.types.TypedSyntaxTreeVisitor.VisitorContext
import wdlTools.types.TypedAbstractSyntax._
import wdlTools.types.WdlTypes._

import scala.annotation.tailrec
import scala.collection.mutable

object TstRules {
  class LinterTstRule(id: String,
                      severity: Severity,
                      docSourceUrl: Option[URL],
                      events: mutable.Buffer[LintEvent])
      extends TypedSyntaxTreeVisitor {
    protected def addEvent(element: TypedAbstractSyntax.Element,
                           message: Option[String] = None): Unit = {
      events.append(LintEvent(id, severity, element.text, docSourceUrl, message))
    }
  }

  type LinterTstRuleApplySig = (
      String,
      Severity,
      WdlVersion,
      Unification,
      mutable.Buffer[LintEvent],
      Option[URL]
  ) => LinterTstRule

  // rules ported from miniwdl

  def isQuestionableCoercion(toType: T,
                             fromType: T,
                             expectedTypes: Set[T],
                             additionalAllowedCoercions: Set[T] = Set.empty): Boolean = {
    val allowedCoercions = expectedTypes ++ additionalAllowedCoercions ++ Set(T_Any)
    (toType, fromType) match {
      case (T_Array(toType, _), T_Array(fromType, _)) =>
        isQuestionableCoercion(toType, fromType, expectedTypes, additionalAllowedCoercions)
      case (T_Map(toKey, toValue), T_Map(fromKey, fromValue)) =>
        isQuestionableCoercion(toKey, fromKey, expectedTypes, additionalAllowedCoercions) ||
          isQuestionableCoercion(toValue, fromValue, expectedTypes, additionalAllowedCoercions)
      case (T_Pair(toLeft, toRight), T_Pair(fromLeft, fromRight)) =>
        isQuestionableCoercion(toLeft, fromLeft, expectedTypes, additionalAllowedCoercions) ||
          isQuestionableCoercion(toRight, fromRight, expectedTypes, additionalAllowedCoercions)
      case (to, from) if expectedTypes.contains(to) => !allowedCoercions.contains(from)
      case _                                        => false
    }
  }

  /**
    * Returns a Vector of all the signatures for the given function,
    * where a signature is a tuple of (param_types, return_type),
    * where param_types is a Vector of the parameter types.
    * @param prototype the function prototype
    * @return
    */
  def getSignature(prototype: T_Function): (Vector[T], T) = {
    prototype match {
      case T_Function0(_, output)                   => (Vector.empty, output)
      case T_Function1(_, arg1, output)             => (Vector(arg1), output)
      case T_Function2(_, arg1, arg2, output)       => (Vector(arg1, arg2), output)
      case T_Function3(_, arg1, arg2, arg3, output) => (Vector(arg1, arg2, arg3), output)
    }
  }

  /**
    * Coercion from non-string-typed expression to string declaration/parameter
    */
  case class StringCoercionRule(id: String,
                                severity: Severity,
                                version: WdlVersion,
                                unification: Unification,
                                events: mutable.Buffer[LintEvent],
                                docSourceUrl: Option[URL])
      extends LinterTstRule(id, severity, docSourceUrl, events) {

    private val stringTypes: Set[T] = Set(T_String, T_File, T_Directory)
    private val stringFunctions: Set[String] = Set(
        "stdout",
        "stderr",
        "glob",
        "length",
        "sub",
        "defined",
        "sep",
        "write_lines",
        "write_tsv",
        "write_map",
        "write_json",
        "read_int",
        "read_boolean",
        "read_string",
        "read_float",
        "read_map",
        "read_lines",
        "read_tsv",
        "read_json"
    )

    override def visitExpression(ctx: VisitorContext[Expr]): Unit = {
      // File-to-String coercions are normal in tasks, but flagged at the workflow level.
      val additionalAllowedCoercions: Set[T] = ctx.findAncestorExecutable match {
        case Some(_: Workflow) => Set.empty
        case _                 => Set(T_File, T_Directory)
      }

      // if this expression is the rhs of a declaration, check that it is coercible to the lhs type
      ctx.getParent.element match {
        case Declaration(_, wdlType, expr, _)
            if expr.isDefined && isQuestionableCoercion(wdlType,
                                                        expr.get.wdlType,
                                                        Set(T_String),
                                                        additionalAllowedCoercions) =>
          addEvent(ctx.element)
        case _ => ()
      }
      // check compatible arguments for operations that take multiple string arguments
      ctx.element match {
        case ExprAdd(a, b, _, _) if ctx.findAncestor[ExprCompoundString].isEmpty =>
          if (!Vector(a, b).exists(_.isInstanceOf[ValueString])) {
            // not within a placeholder and either a or b is a non-literal string type while
            // the other is a non-string type
            val wts = Vector(a, b).map(_.wdlType)
            if (wts.contains(T_String) && !wts.forall(stringTypes.contains)) {
              addEvent(
                  ctx.element,
                  Some(
                      "string concatenation (+) has non-string argument; consider using interpolation"
                  )
              )
            }
          }
        case ExprApply(funcName, prototype, elements, _, _) if stringFunctions.contains(funcName) =>
          // conversion of non-String expression to String-type function parameter
          val (argTypes, _) = getSignature(prototype)
          argTypes.zip(elements.map(_.wdlType)).foreach {
            case (to, from)
                if isQuestionableCoercion(to, from, Set(T_String), additionalAllowedCoercions) =>
              addEvent(ctx.element, Some(s"${to} argument of ${funcName}() = ${from}"))
          }
        case ExprArray(values, _, _) =>
          // mixed string and non-string types in array
          val types = values.map(_.wdlType)
          if (types.contains(T_String) && !types.forall(stringTypes.contains)) {
            addEvent(ctx.element, Some(""))
          }
      }
    }

    override def visitCall(ctx: VisitorContext[Call]): Unit = {
      // TODO: Can't implement this until the Call wdlType includes the inputs
//      ctx.element.inputs.foreach {
//        case (name, expr) =>
//      }
    }
  }

  /**
    * Coercion from string-typed expression to file-typed declaration/parameter
    * that occur somewhere besides task output
    */
  case class FileCoercionRule(id: String,
                              severity: Severity,
                              version: WdlVersion,
                              unification: Unification,
                              events: mutable.Buffer[LintEvent],
                              docSourceUrl: Option[URL])
      extends LinterTstRule(id, severity, docSourceUrl, events) {
    override def visitExpression(ctx: VisitorContext[Expr]): Unit = {
      // ignore coercions in output sections
      if (ctx.findAncestor[OutputSection].isEmpty) {
        ctx.getParent.element match {
          case Declaration(_, wdlType, expr, _)
              if expr.isDefined && isQuestionableCoercion(wdlType,
                                                          expr.get.wdlType,
                                                          Set(T_File, T_Directory)) =>
            addEvent(ctx.element)
          case _ => ()
        }

        ctx.element match {
          case ExprApply(funcName, prototype, elements, _, _) =>
            funcName match {
              case "size" =>
                // calling size() with File?/Array[File?]
                elements(0).wdlType match {
                  case T_Optional(_) => addEvent(ctx.element, Some("File? argument of size()"))
                  case T_Array(T_Optional(_), _) =>
                    addEvent(ctx.element, Some("Array[File?] argument of size()"))
                }
              case _ =>
                // using a String expression for a File-typed function parameter
                val (argTypes, _) = getSignature(prototype)
                argTypes.zip(elements.map(_.wdlType)).foreach {
                  case (to, from) if isQuestionableCoercion(to, from, Set(T_File, T_Directory)) =>
                    addEvent(ctx.element, Some(s"${to} argument of ${funcName}() = ${from}"))
                }
            }
        }
      }
    }

    override def visitCall(ctx: VisitorContext[Call]): Unit = {
      // TODO: Can't implement this until the Call wdlType includes the inputs
      //      ctx.element.inputs.foreach {
      //        case (name, expr) =>
      //      }
    }
  }

  private def isArrayCoercion(toType: T, fromType: T): Boolean = {
    @tailrec
    def arrayLevels(t: T, level: Int = 0): Int = {
      t match {
        case T_Array(t, _) => arrayLevels(t, level + 1)
        case _             => level
      }
    }
    (toType, fromType) match {
      case (_, T_Any) | (_, T_Array(T_Any, _))                => false
      case (a: T_Array, b) if arrayLevels(a) > arrayLevels(b) => true
      case _                                                  => false
    }
  }

  /**
    * Implicit promotion of T -> Array[T]
    */
  case class ArrayCoercionRule(id: String,
                               severity: Severity,
                               version: WdlVersion,
                               unification: Unification,
                               events: mutable.Buffer[LintEvent],
                               docSourceUrl: Option[URL])
      extends LinterTstRule(id, severity, docSourceUrl, events) {
    override def visitExpression(ctx: VisitorContext[Expr]): Unit = {
      ctx.getParent.element match {
        case Declaration(_, wdlType, expr, _)
            if expr.isDefined && isArrayCoercion(wdlType, expr.get.wdlType) =>
          addEvent(ctx.element)
        case _ => ()
      }

      ctx.element match {
        case ExprApply(funcName, prototype, elements, _, _) =>
          val (argTypes, _) = getSignature(prototype)
          argTypes.zip(elements.map(_.wdlType)).foreach {
            case (to, from) if isArrayCoercion(to, from) =>
              addEvent(ctx.element, Some(s"${to} argument of ${funcName}() = ${from}"))
          }
      }
    }

    override def visitCall(ctx: VisitorContext[Call]): Unit = {
      // TODO: Can't implement this until the Call wdlType includes the inputs
      //      ctx.element.inputs.foreach {
      //        case (name, expr) =>
      //      }
    }
  }

  /**
    * Expression of optional type where a non-optional value is expected.
    * These may or may not fail type-checking, depending on the strictness
    * of the type-checking regime and the WDL version.
    */
  case class OptionalCoercionRule(id: String,
                                  severity: Severity,
                                  version: WdlVersion,
                                  unification: Unification,
                                  events: mutable.Buffer[LintEvent],
                                  docSourceUrl: Option[URL])
      extends LinterTstRule(id, severity, docSourceUrl, events) {
    override def visitExpression(ctx: VisitorContext[Expr]): Unit = {
      ctx.getParent.element match {
        case Declaration(_, wdlType, expr, _)
            if expr.isDefined && !(
                unification.isCoercibleTo(expr.get.wdlType, wdlType) ||
                  isArrayCoercion(wdlType, expr.get.wdlType)
            ) =>
          addEvent(ctx.element)
        case _ => ()
      }

      // ignore when within `if (defined(x))`
      val inConditionOnDefined = ctx.findAncestor[Conditional].exists { condCtx =>
        condCtx.element.expr match {
          case ExprApply("defined", _, _, _, _) => true
          case _                                => false
        }
      }
      if (!inConditionOnDefined) {
        ctx.element match {
          case ExprSub(a, b, wdlType, _) | ExprMul(a, b, wdlType, _) =>
          case ExprApply(funcName, prototype, elements, _, _)        =>
          // TODO: can't implement this until ExprApply includes prototype
        }
      }
    }

    override def visitCall(ctx: VisitorContext[Call]): Unit = {
      // TODO: Can't implement this until the Call wdlType includes the inputs
      //      ctx.element.inputs.foreach {
      //        case (name, expr) =>
      //      }
    }
  }

  /**
    * Coercing a possibly-empty array to a declaration or parameter requiring
    * a non-empty array.
    */
  case class NonEmptyCoercionRule(id: String,
                                  severity: Severity,
                                  version: WdlVersion,
                                  unification: Unification,
                                  events: mutable.Buffer[LintEvent],
                                  docSourceUrl: Option[URL])
      extends LinterTstRule(id, severity, docSourceUrl, events) {
    private val ignoreFunctions = Set("glob", "read_lines", "read_tsv", "read_array")

    private def isNonemtpyCoercion(toType: T, fromType: T): Boolean = {
      (toType, fromType) match {
        case (T_Array(_, toNonEmpty), T_Array(_, fromNonEmpty)) if toNonEmpty && !fromNonEmpty =>
          true
        case (T_Map(toKey, toValue), T_Map(fromKey, fromValue)) =>
          isNonemtpyCoercion(toKey, fromKey) || isNonemtpyCoercion(toValue, fromValue)
        case (T_Pair(toLeft, toRight), T_Pair(fromLeft, fromRight)) =>
          isNonemtpyCoercion(toLeft, fromLeft) || isNonemtpyCoercion(toRight, fromRight)
        case _ => false
      }
    }

    override def visitExpression(ctx: VisitorContext[Expr]): Unit = {
      val ignoreDecl = ctx.element match {
        case ExprApply(funcName, prototype, elements, _, _) =>
          // TODO: can't implement this until we have prototype
          ignoreFunctions.contains(funcName)
        case _ => false
      }

      if (!ignoreDecl) {
        ctx.getParent.element match {
          case Declaration(_, wdlType, expr, _)
              if expr.isDefined && isNonemtpyCoercion(wdlType, expr.get.wdlType) =>
            addEvent(ctx.element)
          case _ => ()
        }
      }
    }

    override def visitCall(ctx: VisitorContext[Call]): Unit = {
      // TODO: Can't implement this until the Call wdlType includes the inputs
      //      ctx.element.inputs.foreach {
      //        case (name, expr) =>
      //      }
    }
  }

  // TODO: load these dynamically from a file
  val allRules: Map[String, LinterTstRuleApplySig] = Map(
      "T001" -> StringCoercionRule.apply,
      "T002" -> FileCoercionRule.apply,
      "T003" -> ArrayCoercionRule.apply,
      "T004" -> OptionalCoercionRule.apply,
      "T005" -> NonEmptyCoercionRule.apply
  )
}