package wdlTools.cli

import java.nio.file.Paths

import spray.json.JsValue
import wdlTools.eval.{Eval, Serialize, WdlValues}
import wdlTools.exec.{
  ExecPaths,
  TaskContext,
  TaskExecutor,
  TaskExecutorCommandFailure,
  TaskExecutorInternalError,
  TaskExecutorSuccess
}
import wdlTools.syntax.Parsers
import wdlTools.types.{TypeInfer, TypedAbstractSyntax => TAT}
import wdlTools.util.{FileSourceResolver, FileUtils, JsUtils, Logger, errorMessage}

import scala.language.reflectiveCalls

case class Exec(conf: WdlToolsConf) extends Command {
  override def apply(): Unit = {
    val docSource = FileSourceResolver.get.resolve(conf.exec.uri())
    val (doc: TAT.Document, _) = TypeInfer().apply(Parsers().parseDocument(docSource))
    val taskName = conf.exec.task.toOption
    val tasks = doc.elements.collect {
      case task: TAT.Task if taskName.isEmpty          => task
      case task: TAT.Task if taskName.get == task.name => task
    }
    if (tasks.isEmpty) {
      if (taskName.isEmpty) {
        throw new Exception(s"""WDL file ${docSource} has no tasks""")
      } else {
        throw new Exception(s"""WDL file ${docSource} has no task named ${taskName.get}""")
      }
    }
    if (tasks.size > 1) {
      throw new Exception(
          s"""WDL file ${docSource} has ${tasks.size} tasks - use '--task' 
             |option to specify which one to execute""".stripMargin
      )
    }
    val task = tasks.head
    val inputsString = conf.exec.inputsFile.toOption match {
      case None       => FileUtils.readStdinContent()
      case Some(path) => FileUtils.readFileContent(path)
    }
    val jsInputs: Map[String, JsValue] = if (inputsString.trim.isEmpty) {
      Map.empty
    } else {
      JsUtils.getFields(JsUtils.jsFromString(inputsString))
    }
    val runtimeDefaults: Map[String, WdlValues.V] = conf.exec.runtimeDefaults.toOption match {
      case None => Map.empty
      case Some(path) =>
        JsUtils.getFields(JsUtils.jsFromFile(path)).view.mapValues(Serialize.fromJson).toMap
    }
    val wdlVersion = doc.version.value
    val (hostPaths: ExecPaths, guestPaths: Option[ExecPaths]) = if (conf.exec.container()) {
      val (host, guest) =
        ExecPaths.createLocalContainerPair(containerMountDir = Paths.get("/home/wdlTools"))
      (host, Some(guest))
    } else {
      (ExecPaths.createLocalPathsFromTemp(), None)
    }
    val taskContext = TaskContext.fromJson(
        jsInputs,
        task,
        Eval(hostPaths, wdlVersion),
        guestPaths.map(p => Eval(p, wdlVersion)),
        runtimeDefaults
    )
    val taskExecutor = TaskExecutor(taskContext, hostPaths, guestPaths)
    Logger.get.info(s"""Executing task ${task.name} in ${hostPaths.getRootDir()}""")
    val result = taskExecutor.run
    if (conf.exec.summaryFile.isDefined) {
      JsUtils.jsToFile(taskExecutor.summary, conf.exec.summaryFile())
    }
    result match {
      case TaskExecutorSuccess(_, outputs, _, _) =>
        conf.exec.outputsFile.toOption match {
          case None =>
            System.out.println(JsUtils.jsToString(outputs))
          case Some(path) =>
            JsUtils.jsToFile(outputs, path)
        }
      case TaskExecutorCommandFailure(returnCode, _, stderr) =>
        throw new Exception(
            s"""Task ${task.name} command failed with return code ${returnCode}; stderr:
               |${stderr}""".stripMargin
        )
      case TaskExecutorInternalError(message, error) =>
        throw new Exception(errorMessage(message, error))
    }
  }
}
