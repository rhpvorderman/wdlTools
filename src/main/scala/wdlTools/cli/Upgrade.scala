package wdlTools.cli

import java.nio.file.Files

import wdlTools.generators.code.Upgrader
import wdlTools.util.{FileSourceResolver, LocalFileSource}

import scala.jdk.CollectionConverters._
import scala.language.reflectiveCalls

case class Upgrade(conf: WdlToolsConf) extends Command {
  override def apply(): Unit = {
    val docSource = FileSourceResolver.get.resolve(conf.upgrade.uri())
    val outputDir = conf.upgrade.outputDir.toOption
    val overwrite = conf.upgrade.overwrite()
    val upgrader = Upgrader(conf.upgrade.followImports())
    // write out upgraded versions
    val documents =
      upgrader.upgrade(docSource, conf.upgrade.srcVersion.toOption, conf.upgrade.destVersion())
    documents.foreach {
      case (source, lines) if outputDir.isDefined =>
        Files.write(outputDir.get.resolve(source.fileName), lines.asJava)
      case (localSource: LocalFileSource, lines) if overwrite =>
        Files.write(localSource.localPath, lines.asJava)
      case (_, lines) =>
        println(lines.mkString(System.lineSeparator()))
    }
  }
}
