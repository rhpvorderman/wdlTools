package wdlTools.format

import java.net.URL
import java.nio.file.{Path, Paths}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import wdlTools.eval.{Context, Eval, EvalConfig}
import wdlTools.generators.code
import wdlTools.syntax.v1
import wdlTools.types.{TypeInfer, TypeOptions, TypedAbstractSyntax => TAT}
import wdlTools.util.{SourceCode, Util}

class GeneratorTest extends AnyFlatSpec with Matchers {
  private val opts = TypeOptions()
  private val parser = v1.ParseAll(opts)
  private val typeInfer = TypeInfer(opts)

  def getWdlPath(fname: String, subdir: String): Path = {
    Paths.get(getClass.getResource(s"/format/${subdir}/${fname}").getPath)
  }

  private def getWdlUrl(fname: String, subdir: String): URL = {
    Util.pathToUrl(getWdlPath(fname, subdir))
  }

  it should "handle deep nesting" in {
    val beforeURL = getWdlUrl(fname = "deep_nesting.wdl", subdir = "before")
    val doc = parser.parseDocument(beforeURL)
    val (tDoc, _) = typeInfer.apply(doc)
    val generator = code.WdlV1Generator()
    generator.generateDocument(tDoc)
  }

  it should "handle object values in meta" in {
    val beforeURL = getWdlUrl(fname = "meta_object_values.wdl", subdir = "before")
    val doc = parser.parseDocument(beforeURL)
    val (tDoc, _) = typeInfer.apply(doc)
    val generator = code.WdlV1Generator()
    val gLines = generator.generateDocument(tDoc)
    // test that it parses successfully
    val gDoc = parser.parseDocument(SourceCode(None, gLines))
    typeInfer.apply(gDoc)
  }

  it should "handle workflow with calls" in {
    val beforeURL = getWdlUrl(fname = "wf_with_calL.wdl", subdir = "before")
    val doc = parser.parseDocument(beforeURL)
    val (tDoc, _) = typeInfer.apply(doc)
    val generator = code.WdlV1Generator()
    val gLines = generator.generateDocument(tDoc)
    // test that it parses successfully
    val gDoc = parser.parseDocument(SourceCode(None, gLines))
    typeInfer.apply(gDoc)
    // TODO: test that tDoc == gtDoc
  }

  it should "handle empty calls" in {
    val beforeURL = getWdlUrl(fname = "empty_call.wdl", subdir = "before")
    val doc = parser.parseDocument(beforeURL)
    val (tDoc, _) = typeInfer.apply(doc)
    val generator = code.WdlV1Generator()
    val gLines = generator.generateDocument(tDoc)
    // test that it parses successfully
    val gDoc = parser.parseDocument(SourceCode(None, gLines))
    typeInfer.apply(gDoc)
    // TODO: test that tDoc == gtDoc
  }

  it should "handle optionals" in {
    val beforeURL = getWdlUrl(fname = "optionals.wdl", subdir = "before")
    val doc = parser.parseDocument(beforeURL)
    val (tDoc, _) = typeInfer.apply(doc)
    val generator = code.WdlV1Generator()
    val gLines = generator.generateDocument(tDoc)
    // test that it parses successfully
    val gDoc = parser.parseDocument(SourceCode(None, gLines))
    typeInfer.apply(gDoc)
    // TODO: test that tDoc == gtDoc
  }

  it should "handle command block" in {
    val beforeURL = getWdlUrl(fname = "python_heredoc.wdl", subdir = "before")

    def evalCommand(tDoc: TAT.Document): String = {
      val evaluator = Eval(opts, EvalConfig.empty, wdlTools.syntax.WdlVersion.V1, Some(beforeURL))
      tDoc.elements should not be empty
      val task = tDoc.elements.head.asInstanceOf[TAT.Task]
      val ctx = evaluator.applyDeclarations(task.declarations, Context(Map.empty))
      evaluator.applyCommand(task.command, ctx)
    }

    val expected = """python <<CODE
                     |import os
                     |import sys
                     |print("We are inside a python docker image")
                     |CODE""".stripMargin

    // ensure the source doc command evaluates correctly
    val doc = parser.parseDocument(beforeURL)
    val (tDoc, _) = typeInfer.apply(doc)
    evalCommand(tDoc) shouldBe expected

    // generate, re-parse, and make sure the command still evaluates correctly
    val generator = code.WdlV1Generator()
    val gLines = generator.generateDocument(tDoc)
    // test that it parses successfully
    val gDoc = parser.parseDocument(SourceCode(None, gLines))
    val (gtDoc, _) = typeInfer.apply(gDoc)
    evalCommand(gtDoc) shouldBe expected
  }
}
