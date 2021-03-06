package wdlTools.eval

import wdlTools.eval.WdlValues._
import wdlTools.syntax.{SourceLocation, WdlVersion}
import wdlTools.types.{WdlTypes, TypedAbstractSyntax => TAT}
import wdlTools.util.{FileSourceResolver, Logger}

case class Context(bindings: Map[String, WdlValues.V]) {
  def addBinding(name: String, value: WdlValues.V): Context = {
    assert(!(bindings contains name))
    this.copy(bindings = bindings + (name -> value))
  }
}

object Context {
  def createFromEnv(env: Map[String, (WdlTypes.T, WdlValues.V)]): Context = {
    Context(env.map { case (name, (_, v)) => name -> v })
  }
}

case class Eval(paths: EvalPaths,
                wdlVersion: WdlVersion,
                fileResovler: FileSourceResolver = FileSourceResolver.get,
                logger: Logger = Logger.get) {
  // choose the standard library implementation based on version
  private val standardLibrary = Stdlib(paths, wdlVersion, fileResovler, logger)

  private def getStringVal(value: V, loc: SourceLocation): String = {
    value match {
      case V_Boolean(b) => b.toString
      case V_Int(i)     => i.toString
      case V_Float(x)   => x.toString
      case V_String(s)  => s
      case V_File(s)    => s
      case other        => throw new EvalException(s"bad value ${other}", loc)
    }
  }

  private def compareEqeq(a: V, b: V, loc: SourceLocation): Boolean = {
    (a, b) match {
      case (V_Null, V_Null)               => true
      case (V_Boolean(b1), V_Boolean(b2)) => b1 == b2
      case (V_Int(i1), V_Int(i2))         => i1 == i2
      case (V_Float(x1), V_Float(x2))     => x1 == x2
      case (V_String(s1), V_String(s2))   => s1 == s2
      case (V_File(p1), V_File(p2))       => p1 == p2

      case (V_Pair(l1, r1), V_Pair(l2, r2)) =>
        compareEqeq(l1, l2, loc) && compareEqeq(r1, r2, loc)

      // arrays
      case (V_Array(a1), V_Array(a2)) if a1.size != a2.size => false
      case (V_Array(a1), V_Array(a2))                       =>
        // All array elements must be equal
        (a1 zip a2).forall {
          case (x, y) => compareEqeq(x, y, loc)
        }

      // maps
      case (V_Map(m1), V_Map(m2)) if m1.size != m2.size => false
      case (V_Map(m1), V_Map(m2)) =>
        val keysEqual = (m1.keys.toSet zip m2.keys.toSet).forall {
          case (k1, k2) => compareEqeq(k1, k2, loc)
        }
        if (!keysEqual) {
          false
        } else {
          // now we know the keys are all equal
          m1.keys.forall(k => compareEqeq(m1(k), m2(k), loc))
        }

      // optionals
      case (V_Optional(v1), V_Optional(v2)) =>
        compareEqeq(v1, v2, loc)
      case (V_Optional(v1), v2) =>
        compareEqeq(v1, v2, loc)
      case (v1, V_Optional(v2)) =>
        compareEqeq(v1, v2, loc)

      // structs
      case (V_Struct(name1, _), V_Struct(name2, _)) if name1 != name2 => false
      case (V_Struct(name, members1), V_Struct(_, members2))
          if members1.keys.toSet != members2.keys.toSet =>
        // We need the type definition here. The other option is to assume it has already
        // been cleared at compile time.
        throw new Exception(s"error: struct ${name} does not have the corrent number of members")
      case (V_Struct(_, members1), V_Struct(_, members2)) =>
        members1.keys.forall(k => compareEqeq(members1(k), members2(k), loc))

      case (_: V_Object, _: V_Object) =>
        throw new Exception("objects not implemented")
    }
  }

  private def compareLt(a: V, b: V, loc: SourceLocation): Boolean = {
    (a, b) match {
      case (V_Null, V_Null)             => false
      case (V_Int(n1), V_Int(n2))       => n1 < n2
      case (V_Float(x1), V_Int(n2))     => x1 < n2
      case (V_Int(n1), V_Float(x2))     => n1 < x2
      case (V_Float(x1), V_Float(x2))   => x1 < x2
      case (V_String(s1), V_String(s2)) => s1 < s2
      case (V_File(p1), V_File(p2))     => p1 < p2
      case (_, _) =>
        throw new EvalException("bad value should be a boolean", loc)
    }
  }

  private def compareLte(a: V, b: V, loc: SourceLocation): Boolean = {
    (a, b) match {
      case (V_Null, V_Null)             => true
      case (V_Int(n1), V_Int(n2))       => n1 <= n2
      case (V_Float(x1), V_Int(n2))     => x1 <= n2
      case (V_Int(n1), V_Float(x2))     => n1 <= x2
      case (V_Float(x1), V_Float(x2))   => x1 <= x2
      case (V_String(s1), V_String(s2)) => s1 <= s2
      case (V_File(p1), V_File(p2))     => p1 <= p2
      case (_, _) =>
        throw new EvalException("bad value should be a boolean", loc)
    }
  }

  private def compareGt(a: V, b: V, loc: SourceLocation): Boolean = {
    (a, b) match {
      case (V_Null, V_Null)             => false
      case (V_Int(n1), V_Int(n2))       => n1 > n2
      case (V_Float(x), V_Int(i))       => x > i
      case (V_Int(i), V_Float(x))       => i > x
      case (V_Float(x1), V_Float(x2))   => x1 > x2
      case (V_String(s1), V_String(s2)) => s1 > s2
      case (V_File(p1), V_File(p2))     => p1 > p2
      case (_, _) =>
        throw new EvalException("bad value should be a boolean", loc)
    }
  }

  private def compareGte(a: V, b: V, loc: SourceLocation): Boolean = {
    (a, b) match {
      case (V_Null, V_Null)             => true
      case (V_Int(n1), V_Int(n2))       => n1 >= n2
      case (V_Float(x), V_Int(i))       => x >= i
      case (V_Int(i), V_Float(x))       => i >= x
      case (V_Float(x1), V_Float(x2))   => x1 >= x2
      case (V_String(s1), V_String(s2)) => s1 >= s2
      case (V_File(p1), V_File(p2))     => p1 >= p2
      case (_, _) =>
        throw new EvalException("bad value should be a boolean", loc)
    }
  }

  private def add(a: V, b: V, loc: SourceLocation): V = {
    (a, b) match {
      case (V_Int(n1), V_Int(n2))     => V_Int(n1 + n2)
      case (V_Float(x1), V_Int(n2))   => V_Float(x1 + n2)
      case (V_Int(n1), V_Float(x2))   => V_Float(n1 + x2)
      case (V_Float(x1), V_Float(x2)) => V_Float(x1 + x2)

      // if we are adding strings, the result is a string
      case (V_String(s1), V_String(s2)) => V_String(s1 + s2)
      case (V_String(s1), V_Int(n2))    => V_String(s1 + n2.toString)
      case (V_String(s1), V_Float(x2))  => V_String(s1 + x2.toString)
      case (V_Int(n1), V_String(s2))    => V_String(n1.toString + s2)
      case (V_Float(x1), V_String(s2))  => V_String(x1.toString + s2)
      case (V_String(s), V_Null)        => V_String(s)

      // files
      case (V_File(s1), V_String(s2)) => V_File(s1 + s2)
      case (V_File(s1), V_File(s2))   => V_File(s1 + s2)

      case (_, _) =>
        throw new EvalException("cannot add these values", loc)
    }
  }

  private def sub(a: V, b: V, loc: SourceLocation): V = {
    (a, b) match {
      case (V_Int(n1), V_Int(n2))     => V_Int(n1 - n2)
      case (V_Float(x1), V_Int(n2))   => V_Float(x1 - n2)
      case (V_Int(n1), V_Float(x2))   => V_Float(n1 - x2)
      case (V_Float(x1), V_Float(x2)) => V_Float(x1 - x2)
      case (_, _) =>
        throw new EvalException(s"Expressions must be integers or floats", loc)
    }
  }

  private def mod(a: V, b: V, loc: SourceLocation): V = {
    (a, b) match {
      case (V_Int(n1), V_Int(n2))     => V_Int(n1 % n2)
      case (V_Float(x1), V_Int(n2))   => V_Float(x1 % n2)
      case (V_Int(n1), V_Float(x2))   => V_Float(n1 % x2)
      case (V_Float(x1), V_Float(x2)) => V_Float(x1 % x2)
      case (_, _) =>
        throw new EvalException(s"Expressions must be integers or floats", loc)
    }
  }

  private def multiply(a: V, b: V, loc: SourceLocation): V = {
    (a, b) match {
      case (V_Int(n1), V_Int(n2))     => V_Int(n1 * n2)
      case (V_Float(x1), V_Int(n2))   => V_Float(x1 * n2)
      case (V_Int(n1), V_Float(x2))   => V_Float(n1 * x2)
      case (V_Float(x1), V_Float(x2)) => V_Float(x1 * x2)
      case (_, _) =>
        throw new EvalException(s"Expressions must be integers or floats", loc)
    }
  }

  private def divide(a: V, b: V, loc: SourceLocation): V = {
    (a, b) match {
      case (V_Int(n1), V_Int(n2)) =>
        if (n2 == 0)
          throw new EvalException("DivisionByZero", loc)
        V_Int(n1 / n2)
      case (V_Float(x1), V_Int(n2)) =>
        if (n2 == 0)
          throw new EvalException("DivisionByZero", loc)
        V_Float(x1 / n2)
      case (V_Int(n1), V_Float(x2)) =>
        if (x2 == 0)
          throw new EvalException("DivisionByZero", loc)
        V_Float(n1 / x2)
      case (V_Float(x1), V_Float(x2)) =>
        if (x2 == 0)
          throw new EvalException("DivisionByZero", loc)
        V_Float(x1 / x2)
      case (_, _) =>
        throw new EvalException(s"Expressions must be integers or floats", loc)
    }
  }

  // Access a field in a struct or an object. For example:
  //   Int z = x.a
  private def exprGetName(value: V, id: String, loc: SourceLocation) = {
    value match {
      case V_Struct(name, members) =>
        members.get(id) match {
          case None =>
            throw new EvalException(s"Struct ${name} does not have member ${id}", loc)
          case Some(t) => t
        }

      case V_Object(members) =>
        members.get(id) match {
          case None =>
            throw new EvalException(s"Object does not have member ${id}", loc)
          case Some(t) => t
        }

      case V_Call(name, members) =>
        members.get(id) match {
          case None =>
            throw new EvalException(s"Call object ${name} does not have member ${id}", loc)
          case Some(t) => t
        }

      // accessing a pair element
      case V_Pair(l, _) if id.toLowerCase() == "left"  => l
      case V_Pair(_, r) if id.toLowerCase() == "right" => r
      case V_Pair(_, _) =>
        throw new EvalException(s"accessing a pair with (${id}) is illegal", loc)

      case _ =>
        throw new EvalException(s"member access (${id}) in expression is illegal", loc)
    }
  }

  private def apply(expr: TAT.Expr, ctx: Context): WdlValues.V = {
    expr match {
      case _: TAT.ValueNull    => V_Null
      case x: TAT.ValueBoolean => V_Boolean(x.value)
      case x: TAT.ValueInt     => V_Int(x.value)
      case x: TAT.ValueFloat   => V_Float(x.value)
      case x: TAT.ValueString  => V_String(x.value)
      case x: TAT.ValueFile    => V_File(x.value)

      // accessing a variable
      case eid: TAT.ExprIdentifier if !(ctx.bindings contains eid.id) =>
        throw new EvalException(s"accessing undefined variable ${eid.id}")
      case eid: TAT.ExprIdentifier =>
        ctx.bindings(eid.id)

      // concatenate an array of strings inside a command block
      case ecs: TAT.ExprCompoundString =>
        val strArray: Vector[String] = ecs.value.map { x =>
          val xv = apply(x, ctx)
          getStringVal(xv, x.loc)
        }
        V_String(strArray.mkString(""))

      case ep: TAT.ExprPair => V_Pair(apply(ep.l, ctx), apply(ep.r, ctx))
      case ea: TAT.ExprArray =>
        V_Array(ea.value.map { x =>
          apply(x, ctx)
        })
      case em: TAT.ExprMap =>
        V_Map(em.value.map {
          case (k, v) => apply(k, ctx) -> apply(v, ctx)
        })

      case eObj: TAT.ExprObject =>
        V_Object(eObj.value.map {
          case (k, v) =>
            // an object literal key can be a string or identifier
            val key = apply(k, ctx) match {
              case V_String(s) => s
              case _ =>
                throw new EvalException(s"bad value ${k}, object literal key must be a string",
                                        expr.loc)
            }
            key -> apply(v, ctx)
        })

      // ~{true="--yes" false="--no" boolean_value}
      case TAT.ExprPlaceholderEqual(t, f, boolExpr, _, _) =>
        apply(boolExpr, ctx) match {
          case V_Boolean(true)  => apply(t, ctx)
          case V_Boolean(false) => apply(f, ctx)
          case other =>
            throw new EvalException(s"bad value ${other}, should be a boolean", expr.loc)
        }

      // ~{default="foo" optional_value}
      case TAT.ExprPlaceholderDefault(defaultVal, optVal, _, _) =>
        apply(optVal, ctx) match {
          case V_Null => apply(defaultVal, ctx)
          case other  => other
        }

      // ~{sep=", " array_value}
      case TAT.ExprPlaceholderSep(sep: TAT.Expr, arrayVal: TAT.Expr, _, _) =>
        val sep2 = getStringVal(apply(sep, ctx), sep.loc)
        apply(arrayVal, ctx) match {
          case V_Array(ar) =>
            val elements: Vector[String] = ar.map { x =>
              getStringVal(x, expr.loc)
            }
            V_String(elements.mkString(sep2))
          case other =>
            throw new EvalException(s"bad value ${other}, should be a string", expr.loc)
        }

      // operators on one argument
      case e: TAT.ExprUnaryPlus =>
        apply(e.value, ctx) match {
          case V_Float(f) => V_Float(f)
          case V_Int(k)   => V_Int(k)
          case other =>
            throw new EvalException(s"bad value ${other}, should be a number", expr.loc)
        }

      case e: TAT.ExprUnaryMinus =>
        apply(e.value, ctx) match {
          case V_Float(f) => V_Float(-1 * f)
          case V_Int(k)   => V_Int(-1 * k)
          case other =>
            throw new EvalException(s"bad value ${other}, should be a number", expr.loc)
        }

      case e: TAT.ExprNegate =>
        apply(e.value, ctx) match {
          case V_Boolean(b) => V_Boolean(!b)
          case other =>
            throw new EvalException(s"bad value ${other}, should be a boolean", expr.loc)
        }

      // operators on two arguments
      case TAT.ExprLor(a, b, _, _) =>
        val av = apply(a, ctx)
        val bv = apply(b, ctx)
        (av, bv) match {
          case (V_Boolean(a1), V_Boolean(b1)) =>
            V_Boolean(a1 || b1)
          case (V_Boolean(_), other) =>
            throw new EvalException(s"bad value ${other}, should be a boolean", b.loc)
          case (other, _) =>
            throw new EvalException(s"bad value ${other}, should be a boolean", a.loc)
        }

      case TAT.ExprLand(a, b, _, _) =>
        val av = apply(a, ctx)
        val bv = apply(b, ctx)
        (av, bv) match {
          case (V_Boolean(a1), V_Boolean(b1)) =>
            V_Boolean(a1 && b1)
          case (V_Boolean(_), other) =>
            throw new EvalException(s"bad value ${other}, should be a boolean", b.loc)
          case (other, _) =>
            throw new EvalException(s"bad value ${other}, should be a boolean", a.loc)
        }

      // recursive comparison
      case TAT.ExprEqeq(a, b, _, loc) =>
        val av = apply(a, ctx)
        val bv = apply(b, ctx)
        V_Boolean(compareEqeq(av, bv, loc))
      case TAT.ExprNeq(a, b, _, loc) =>
        val av = apply(a, ctx)
        val bv = apply(b, ctx)
        V_Boolean(!compareEqeq(av, bv, loc))

      case TAT.ExprLt(a, b, _, loc) =>
        val av = apply(a, ctx)
        val bv = apply(b, ctx)
        V_Boolean(compareLt(av, bv, loc))
      case TAT.ExprLte(a, b, _, loc) =>
        val av = apply(a, ctx)
        val bv = apply(b, ctx)
        V_Boolean(compareLte(av, bv, loc))
      case TAT.ExprGt(a, b, _, loc) =>
        val av = apply(a, ctx)
        val bv = apply(b, ctx)
        V_Boolean(compareGt(av, bv, loc))
      case TAT.ExprGte(a, b, _, loc) =>
        val av = apply(a, ctx)
        val bv = apply(b, ctx)
        V_Boolean(compareGte(av, bv, loc))

      // Add is overloaded, can be used to add numbers or concatenate strings
      case TAT.ExprAdd(a, b, _, loc) =>
        val av = apply(a, ctx)
        val bv = apply(b, ctx)
        add(av, bv, loc)

      // Math operations
      case TAT.ExprSub(a, b, _, loc) =>
        val av = apply(a, ctx)
        val bv = apply(b, ctx)
        sub(av, bv, loc)
      case TAT.ExprMod(a, b, _, loc) =>
        val av = apply(a, ctx)
        val bv = apply(b, ctx)
        mod(av, bv, loc)
      case TAT.ExprMul(a, b, _, loc) =>
        val av = apply(a, ctx)
        val bv = apply(b, ctx)
        multiply(av, bv, loc)
      case TAT.ExprDivide(a, b, _, loc) =>
        val av = apply(a, ctx)
        val bv = apply(b, ctx)
        divide(av, bv, loc)

      // Access an array element at [index]
      case TAT.ExprAt(array, index, _, loc) =>
        val array_v = apply(array, ctx)
        val index_v = apply(index, ctx)
        (array_v, index_v) match {
          case (V_Array(av), V_Int(n)) if n < av.size =>
            av(n.toInt)
          case (V_Array(av), V_Int(n)) =>
            val arraySize = av.size
            throw new EvalException(
                s"array access out of bounds (size=${arraySize}, element accessed=${n})",
                loc
            )
          case (_, _) =>
            throw new EvalException(s"array access requires an array and an integer", loc)
        }

      // conditional:
      // if (x == 1) then "Sunday" else "Weekday"
      case TAT.ExprIfThenElse(cond, tBranch, fBranch, _, loc) =>
        val cond_v = apply(cond, ctx)
        cond_v match {
          case V_Boolean(true)  => apply(tBranch, ctx)
          case V_Boolean(false) => apply(fBranch, ctx)
          case _ =>
            throw new EvalException(s"condition is not boolean", loc)
        }

      // Apply a standard library function to arguments. For example:
      //   read_int("4")
      case TAT.ExprApply(funcName, _, elements, _, loc) =>
        val funcArgs = elements.map(e => apply(e, ctx))
        standardLibrary.call(funcName, funcArgs, loc)

      // Access a field in a struct or an object. For example:
      //   Int z = x.a
      //
      // shortcut. The environment has a bindings for "x.a"
      case TAT.ExprGetName(TAT.ExprIdentifier(id, _: WdlTypes.T_Call, _), fieldName, _, _)
          if ctx.bindings contains s"$id.$fieldName" =>
        ctx.bindings(s"$id.$fieldName")

      // normal path, first, evaluate the expression "x" then access field "a"
      case TAT.ExprGetName(e: TAT.Expr, fieldName, _, loc) =>
        val ev = apply(e, ctx)
        exprGetName(ev, fieldName, loc)

      case other =>
        throw new Exception(s"sanity: expression ${other} not implemented")
    }
  }

  // public entry points
  //
  def applyExpr(expr: TAT.Expr, ctx: Context): WdlValues.V = {
    apply(expr, ctx)
  }

  // cast the result value to the correct type
  // For example, an expression like:
  //   Float x = "3.2"
  // requires casting from string to float
  def applyExprAndCoerce(expr: TAT.Expr, wdlType: WdlTypes.T, ctx: Context): WdlValues.V = {
    val value = apply(expr, ctx)
    Coercion.coerceTo(wdlType, value, expr.loc)
  }

  def applyExprAndCoerce(expr: TAT.Expr,
                         wdlTypes: Vector[WdlTypes.T],
                         ctx: Context): WdlValues.V = {
    val value = apply(expr, ctx)
    Coercion.coerceToFirst(wdlTypes, value, expr.loc)
  }

  // Evaluate all the declarations and return a Context
  def applyDeclarations(decls: Vector[TAT.Declaration], ctx: Context): Context = {
    decls.foldLeft(ctx) {
      case (accu, TAT.Declaration(name, wdlType, Some(expr), loc)) =>
        val value = apply(expr, accu)
        val coerced = Coercion.coerceTo(wdlType, value, loc)
        accu.addBinding(name, coerced)
      case (_, ast) =>
        throw new Exception(s"Cannot evaluate element ${ast.getClass}")
    }
  }

  /**
    * Given a multi-line string, determine the largest w such that each line
    * begins with at least w whitespace characters.
    * @param s the string to trim
    * @param ignoreEmptyLines ignore empty lines
    * @param lineSep character to use to separate lines in the returned String
    * @return tuple (lineOffset, colOffset, trimmedString) where lineOffset
    *  is the number of lines trimmed from the beginning of the string,
    *  colOffset is the number of whitespace characters trimmed from the
    *  beginning of the line containing the first non-whitespace character,
    *  and trimmedString is `s` with all all prefix and suffix whitespace
    *  trimmed, as well as `w` whitespace characters trimmed from the
    *  beginning of each line.
    *  @example
    *    val s = "   \n  hello\n   goodbye\n "
    *    stripLeadingWhitespace(s, false) => (1, 1, "hello\n  goodbye\n")
    *     stripLeadingWhitespace(s, true) => (1, 2, "hello\n goodbye")
    */
  private def stripLeadingWhitespace(
      s: String,
      ignoreEmptyLines: Boolean = true,
      lineSep: String = System.lineSeparator()
  ): String = {
    val lines = s.split("\r\n?|\n")
    val wsRegex = "^([ \t]*)$".r
    val nonWsRegex = "^([ \t]*)(.+)$".r
    val (_, content) = lines.foldLeft((0, Vector.empty[(String, String)])) {
      case ((lineOffset, content), wsRegex(txt)) =>
        if (content.isEmpty) {
          (lineOffset + 1, content)
        } else if (ignoreEmptyLines) {
          (lineOffset, content)
        } else {
          (lineOffset, content :+ (txt, ""))
        }
      case ((lineOffset, content), nonWsRegex(ws, txt)) => (lineOffset, content :+ (ws, txt))
    }
    if (content.isEmpty) {
      ""
    } else {
      val (whitespace, strippedLines) = content.unzip
      val colOffset = whitespace.map(_.length).min
      val strippedContent = (
          if (colOffset == 0) {
            strippedLines
          } else {
            // add back to each line any whitespace longer than colOffset
            strippedLines.zip(whitespace).map {
              case (line, ws) if ws.length > colOffset => ws.drop(colOffset) + line
              case (line, _)                           => line
            }
          }
      ).mkString(lineSep)
      strippedContent
    }
  }

  // evaluate all the parts of a command section.
  //
  def applyCommand(command: TAT.CommandSection, ctx: Context): String = {
    val commandStr = command.parts
      .map { expr =>
        val value = apply(expr, ctx)
        val str = Serialize.primitiveValueToString(value, expr.loc)
        str
      }
      .mkString("")
    // strip off common leading whitespace
    stripLeadingWhitespace(commandStr)
  }
}
