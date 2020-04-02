package wdlTools.format
import java.nio.file.{Path, Paths}

import org.scalatest.{FlatSpec, Matchers}
import wdlTools.formatter.WDL10Formatter
import wdlTools.syntax.ParseAll
import wdlTools.util.{Options, URL, Util}

class BaseTest extends FlatSpec with Matchers {
  private lazy val conf = Options(antlr4Trace = false)

  def getWdlPath(fname: String, subdir: String): Path = {
    Paths.get(getClass.getResource(s"/format/${subdir}/${fname}").getPath)
  }

  private def getWdlURL(fname: String, subdir: String): URL = {
    URL(getWdlPath(fname, subdir).toString)
  }

  it should "handle the runtime section correctly" in {
    val parser = ParseAll(conf)
    val srcURL = getWdlURL(fname = "simple.wdl", subdir = "after")
    val doc = parser.apply(srcURL)
    doc.version shouldBe "1.0"
  }

  def getWdlSource(fname: String, subdir: String): String = {
    Util.readFromFile(getWdlPath(fname, subdir))
  }

  it should "reformat simple WDL" in {
    val beforeURI = getWdlPath(fname = "simple.wdl", subdir = "before").toUri
    val expected = getWdlSource(fname = "simple.wdl", subdir = "after")
    val formatter = WDL10Formatter(conf)
    formatter.formatDocuments(beforeURI)
    formatter.documents(beforeURI).mkString("\n") shouldBe expected
  }
}