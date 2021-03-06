package wdlTools.cli

import wdlTools.generators.Renderer
import wdlTools.generators.project.ReadmeGenerator
import wdlTools.syntax.Parsers
import wdlTools.util.{FileSource, FileSourceResolver}

import scala.language.reflectiveCalls

case class Readmes(conf: WdlToolsConf) extends Command {
  override def apply(): Unit = {
    val docSource = FileSourceResolver.get.resolve(conf.readmes.uri())
    val parsers = Parsers(conf.readmes.followImports())
    val renderer = Renderer()
    val readmes = parsers.getDocumentWalker[Vector[FileSource]](docSource, Vector.empty).walk {
      (doc, results) =>
        results ++ ReadmeGenerator(conf.readmes.developerReadmes(), renderer).apply(doc)
    }
    FileSource.localizeAll(readmes,
                           outputDir = conf.readmes.outputDir.toOption,
                           overwrite = conf.readmes.overwrite())
  }
}
