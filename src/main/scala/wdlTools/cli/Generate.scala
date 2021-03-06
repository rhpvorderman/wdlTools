package wdlTools.cli

import java.nio.file.{Path, Paths}

import wdlTools.generators.project.ProjectGenerator.{TaskModel, WorkflowModel}
import wdlTools.generators.project.ProjectGenerator
import wdlTools.util.FileSource

import scala.language.reflectiveCalls

case class Generate(conf: WdlToolsConf) extends Command {
  override def apply(): Unit = {
    val args = conf.generate
    val name = args.name()
    val outputDir: Path = args.outputDir.getOrElse(Paths.get(name))
    val generator = ProjectGenerator(
        name,
        wdlVersion = args.wdlVersion(),
        interactive = args.interactive(),
        readmes = args.readmes(),
        dockerfile = args.dockerfile(),
        tests = args.tests(),
        makefile = args.makefile(),
        dockerImage = args.docker.toOption
    )
    val workflow = if (args.workflow()) {
      Some(WorkflowModel(args.wdlVersion(), name = Some(name)))
    } else {
      None
    }
    val tasks =
      args.task.map(_.map(taskName => TaskModel(Some(taskName))).toVector).getOrElse(Vector.empty)
    val generatedFiles = generator.apply(workflow, tasks)
    FileSource.localizeAll(generatedFiles, Some(outputDir), args.overwrite())
  }
}
