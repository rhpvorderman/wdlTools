package wdlTools.syntax.v1

import wdlTools.syntax.{AbstractSyntax => AST, SyntaxException}
import wdlTools.syntax.v1.{ConcreteSyntax => CST}

object Translators {
  def translateType(t: CST.Type): AST.Type = {
    t match {
      case CST.TypeOptional(t, srcText) =>
        AST.TypeOptional(translateType(t), srcText)
      case CST.TypeArray(t, nonEmpty, srcText) =>
        AST.TypeArray(translateType(t), nonEmpty, srcText)
      case CST.TypeMap(k, v, srcText) =>
        AST.TypeMap(translateType(k), translateType(v), srcText)
      case CST.TypePair(l, r, srcText) =>
        AST.TypePair(translateType(l), translateType(r), srcText)
      case CST.TypeString(srcText)         => AST.TypeString(srcText)
      case CST.TypeFile(srcText)           => AST.TypeFile(srcText)
      case CST.TypeBoolean(srcText)        => AST.TypeBoolean(srcText)
      case CST.TypeInt(srcText)            => AST.TypeInt(srcText)
      case CST.TypeFloat(srcText)          => AST.TypeFloat(srcText)
      case CST.TypeIdentifier(id, srcText) => AST.TypeIdentifier(id, srcText)
      case CST.TypeObject(srcText)         => AST.TypeObject(srcText)
      case CST.TypeStruct(name, members, srcText, _) =>
        AST.TypeStruct(name, members.map {
          case CST.StructMember(name, t, text, _) =>
            AST.StructMember(name, translateType(t), text)
        }, srcText)
    }
  }

  def translateExpr(e: CST.Expr): AST.Expr = {
    e match {
      // values
      case CST.ExprString(value, srcText)  => AST.ValueString(value, srcText)
      case CST.ExprFile(value, srcText)    => AST.ValueFile(value, srcText)
      case CST.ExprBoolean(value, srcText) => AST.ValueBoolean(value, srcText)
      case CST.ExprInt(value, srcText)     => AST.ValueInt(value, srcText)
      case CST.ExprFloat(value, srcText)   => AST.ValueFloat(value, srcText)

      // compound values
      case CST.ExprIdentifier(id, srcText) => AST.ExprIdentifier(id, srcText)
      case CST.ExprCompoundString(vec, srcText) =>
        AST.ExprCompoundString(vec.map(translateExpr), srcText)
      case CST.ExprPair(l, r, srcText) =>
        AST.ExprPair(translateExpr(l), translateExpr(r), srcText)
      case CST.ExprArrayLiteral(vec, srcText) =>
        AST.ExprArray(vec.map(translateExpr), srcText)
      case CST.ExprMapLiteral(m, srcText) =>
        AST.ExprMap(m.map { item =>
          AST.ExprMapItem(translateExpr(item.key), translateExpr(item.value), item.text)
        }, srcText)
      case CST.ExprObjectLiteral(m, srcText) =>
        AST.ExprObject(m.map { member =>
          AST.ExprObjectMember(member.key, translateExpr(member.value), member.text)
        }, srcText)

      // string place holders
      case CST.ExprPlaceholderEqual(t, f, value, srcText) =>
        AST.ExprPlaceholderEqual(translateExpr(t), translateExpr(f), translateExpr(value), srcText)
      case CST.ExprPlaceholderDefault(default, value, srcText) =>
        AST.ExprPlaceholderDefault(translateExpr(default), translateExpr(value), srcText)
      case CST.ExprPlaceholderSep(sep, value, srcText) =>
        AST.ExprPlaceholderSep(translateExpr(sep), translateExpr(value), srcText)

      // operators on one argument
      case CST.ExprUniraryPlus(value, srcText) =>
        AST.ExprUniraryPlus(translateExpr(value), srcText)
      case CST.ExprUniraryMinus(value, srcText) =>
        AST.ExprUniraryMinus(translateExpr(value), srcText)
      case CST.ExprNegate(value, srcText) =>
        AST.ExprNegate(translateExpr(value), srcText)

      // operators on two arguments
      case CST.ExprLor(a, b, srcText) =>
        AST.ExprLor(translateExpr(a), translateExpr(b), srcText)
      case CST.ExprLand(a, b, srcText) =>
        AST.ExprLand(translateExpr(a), translateExpr(b), srcText)
      case CST.ExprEqeq(a, b, srcText) =>
        AST.ExprEqeq(translateExpr(a), translateExpr(b), srcText)
      case CST.ExprLt(a, b, srcText) =>
        AST.ExprLt(translateExpr(a), translateExpr(b), srcText)
      case CST.ExprGte(a, b, srcText) =>
        AST.ExprGte(translateExpr(a), translateExpr(b), srcText)
      case CST.ExprNeq(a, b, srcText) =>
        AST.ExprNeq(translateExpr(a), translateExpr(b), srcText)
      case CST.ExprLte(a, b, srcText) =>
        AST.ExprLte(translateExpr(a), translateExpr(b), srcText)
      case CST.ExprGt(a, b, srcText) =>
        AST.ExprGt(translateExpr(a), translateExpr(b), srcText)
      case CST.ExprAdd(a, b, srcText) =>
        AST.ExprAdd(translateExpr(a), translateExpr(b), srcText)
      case CST.ExprSub(a, b, srcText) =>
        AST.ExprSub(translateExpr(a), translateExpr(b), srcText)
      case CST.ExprMod(a, b, srcText) =>
        AST.ExprMod(translateExpr(a), translateExpr(b), srcText)
      case CST.ExprMul(a, b, srcText) =>
        AST.ExprMul(translateExpr(a), translateExpr(b), srcText)
      case CST.ExprDivide(a, b, srcText) =>
        AST.ExprDivide(translateExpr(a), translateExpr(b), srcText)

      // Access an array element at [index]
      case CST.ExprAt(array, index, srcText) =>
        AST.ExprAt(translateExpr(array), translateExpr(index), srcText)

      case CST.ExprIfThenElse(cond, tBranch, fBranch, srcText) =>
        AST.ExprIfThenElse(translateExpr(cond),
                           translateExpr(tBranch),
                           translateExpr(fBranch),
                           srcText)
      case CST.ExprApply(funcName, elements, srcText) =>
        AST.ExprApply(funcName, elements.map(translateExpr), srcText)
      case CST.ExprGetName(e, id, srcText) =>
        AST.ExprGetName(translateExpr(e), id, srcText)

      case other =>
        throw new Exception(s"invalid concrete syntax element ${other}")
    }
  }

  // The meta values are a subset of the expression syntax.
  //
  // $meta_value = $string | $number | $boolean | 'null' | $meta_object | $meta_array
  // $meta_object = '{}' | '{' $parameter_meta_kv (, $parameter_meta_kv)* '}'
  // $meta_array = '[]' |  '[' $meta_value (, $meta_value)* ']'
  //
  private def metaValueToExpr(value: CST.Expr): AST.Expr = {
    value match {
      // values
      case CST.ExprString(value, srcText)  => AST.ValueString(value, srcText)
      case CST.ExprFile(value, srcText)    => AST.ValueFile(value, srcText)
      case CST.ExprBoolean(value, srcText) => AST.ValueBoolean(value, srcText)
      case CST.ExprInt(value, srcText)     => AST.ValueInt(value, srcText)
      case CST.ExprFloat(value, srcText)   => AST.ValueFloat(value, srcText)

      // special handling for null. It appears as an identifier here, even though
      // it has not been defined, and it is no identifier.
      case CST.ExprIdentifier(id, srcText) if id == "null" =>
        AST.ExprIdentifier(id, srcText)
      case CST.ExprIdentifier(id, srcText) =>
        throw new SyntaxException(s"cannot access identifier (${id}) in a meta section", srcText)

      // compound values
      case CST.ExprPair(l, r, srcText) =>
        AST.ExprPair(metaValueToExpr(l), metaValueToExpr(r), srcText)
      case CST.ExprArrayLiteral(vec, srcText) =>
        AST.ExprArray(vec.map(metaValueToExpr), srcText)
      case CST.ExprMapLiteral(m, srcText) =>
        AST.ExprMap(
            m.map {
              case CST.ExprMapItem(CST.ExprIdentifier(k, text2), value, text) =>
                AST.ExprMapItem(AST.ExprIdentifier(k, text2), metaValueToExpr(value), text)
              case _ => throw new SyntaxException(s"Illegal meta field value", srcText)
            },
            srcText
        )
      case CST.ExprObjectLiteral(m, srcText) =>
        AST.ExprObject(m.map {
          case CST.ExprObjectMember(fieldName, v, text) =>
            AST.ExprObjectMember(fieldName, metaValueToExpr(v), text)
        }, srcText)

      case other =>
        throw new SyntaxException("illegal expression in meta section", other.text)
    }
  }

  def translateMetaKV(kv: CST.MetaKV): AST.MetaKV = {
    AST.MetaKV(kv.id, metaValueToExpr(kv.expr), kv.text, kv.comment)
  }

  def translateInputSection(
      inp: CST.InputSection
  ): AST.InputSection = {
    AST.InputSection(inp.declarations.map(translateDeclaration), inp.text, inp.comment)
  }

  def translateOutputSection(
      output: CST.OutputSection
  ): AST.OutputSection = {
    AST.OutputSection(output.declarations.map(translateDeclaration), output.text, output.comment)
  }

  def translateCommandSection(
      cs: CST.CommandSection
  ): AST.CommandSection = {
    AST.CommandSection(cs.parts.map(translateExpr), cs.text, cs.comment)
  }

  def translateDeclaration(decl: CST.Declaration): AST.Declaration = {
    AST.Declaration(decl.name,
                    translateType(decl.wdlType),
                    decl.expr.map(translateExpr),
                    decl.text,
                    decl.comment)
  }

  def translateMetaSection(meta: CST.MetaSection): AST.MetaSection = {
    AST.MetaSection(meta.kvs.map(translateMetaKV), meta.text, meta.comment)
  }

  def translateParameterMetaSection(
      paramMeta: CST.ParameterMetaSection
  ): AST.ParameterMetaSection = {
    AST.ParameterMetaSection(paramMeta.kvs.map(translateMetaKV), paramMeta.text, paramMeta.comment)
  }

  def translateRuntimeSection(
      runtime: CST.RuntimeSection
  ): AST.RuntimeSection = {
    AST.RuntimeSection(
        runtime.kvs.map {
          case CST.RuntimeKV(id, expr, text, comment) =>
            AST.RuntimeKV(id, translateExpr(expr), text, comment)
        },
        runtime.text,
        runtime.comment
    )
  }

  def translateWorkflowElement(
      elem: CST.WorkflowElement
  ): AST.WorkflowElement = {
    elem match {
      case CST.Declaration(name, wdlType, expr, text, comment) =>
        AST.Declaration(name, translateType(wdlType), expr.map(translateExpr), text, comment)

      case CST.Call(name, alias, inputs, text, comment) =>
        AST.Call(
            name,
            alias.map {
              case CST.CallAlias(callName, callText) =>
                AST.CallAlias(callName, callText)
            },
            inputs.map {
              case CST.CallInputs(inputsMap, inputsText) =>
                AST.CallInputs(inputsMap.map { inp =>
                  AST.CallInput(inp.name, translateExpr(inp.expr), inp.text)
                }, inputsText)
            },
            text,
            comment
        )

      case CST.Scatter(identifier, expr, body, text, comment) =>
        AST.Scatter(identifier,
                    translateExpr(expr),
                    body.map(translateWorkflowElement),
                    text,
                    comment)

      case CST.Conditional(expr, body, text, comment) =>
        AST.Conditional(translateExpr(expr), body.map(translateWorkflowElement), text, comment)
    }
  }

  def translateWorkflow(wf: CST.Workflow): AST.Workflow = {
    AST.Workflow(
        wf.name,
        wf.input.map(translateInputSection),
        wf.output.map(translateOutputSection),
        wf.meta.map(translateMetaSection),
        wf.parameterMeta.map(translateParameterMetaSection),
        wf.body.map(translateWorkflowElement),
        wf.text,
        wf.comment
    )
  }

  def translateStruct(struct: CST.TypeStruct): AST.TypeStruct = {
    AST.TypeStruct(
        struct.name,
        struct.members.map {
          case CST.StructMember(name, t, memberText, memberComment) =>
            AST.StructMember(name, translateType(t), memberText, memberComment)
        },
        struct.text,
        struct.comment
    )
  }

  def translateImportDoc(importDoc: CST.ImportDoc,
                         importedDoc: Option[AST.Document]): AST.ImportDoc = {
    val addrAbst = AST.ImportAddr(importDoc.addr.value, importDoc.text)
    val nameAbst = importDoc.name.map {
      case CST.ImportName(value, text) => AST.ImportName(value, text)
    }
    val aliasesAbst: Vector[AST.ImportAlias] = importDoc.aliases.map {
      case CST.ImportAlias(x, y, alText) => AST.ImportAlias(x, y, alText)
    }

    // Replace the original statement with a new one
    AST.ImportDoc(nameAbst, aliasesAbst, addrAbst, importedDoc, importDoc.text, importDoc.comment)
  }

  def translateTask(task: CST.Task): AST.Task = {
    AST.Task(
        task.name,
        task.input.map(translateInputSection),
        task.output.map(translateOutputSection),
        translateCommandSection(task.command),
        task.declarations.map(translateDeclaration),
        task.meta.map(translateMetaSection),
        task.parameterMeta.map(translateParameterMetaSection),
        task.runtime.map(translateRuntimeSection),
        task.text,
        task.comment
    )
  }
}