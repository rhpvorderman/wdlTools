package wdlTools.util

import java.lang.management.ManagementFactory
import java.nio.file.Path

import scala.concurrent.{Await, Future, TimeoutException, blocking, duration}
import scala.concurrent.ExecutionContext.Implicits._
import scala.sys.process.{Process, ProcessLogger}

object SysUtils {
  def execScript(script: Path,
                 timeout: Option[Int] = None,
                 logger: Logger = Logger.Quiet,
                 allowedReturnCodes: Set[Int] = Set(0),
                 exceptionOnFailure: Boolean = true): (Int, String, String) = {
    // sh -c executes the commands in 'script' when the argument is a file
    execCommand(script.toString, timeout, logger, allowedReturnCodes, exceptionOnFailure)
  }

  /**
    * Runs a child process using `/bin/sh -c`.
    * @param command the command to run
    * @param timeout seconds to wait before killing the process, or None to wait indefinitely
    * @param logger Logger to use for logging a command failure
    */
  def execCommand(command: String,
                  timeout: Option[Int] = None,
                  logger: Logger = Logger.Quiet,
                  allowedReturnCodes: Set[Int] = Set(0),
                  exceptionOnFailure: Boolean = true): (Int, String, String) = {
    val cmds = Seq("/bin/sh", "-c", command)
    val outStream = new StringBuilder()
    val errStream = new StringBuilder()
    def getStds: (String, String) = (outStream.toString, errStream.toString)

    val procLogger = ProcessLogger(
        (o: String) => { outStream.append(o ++ "\n") },
        (e: String) => { errStream.append(e ++ "\n") }
    )
    val p: Process = Process(cmds).run(procLogger, connectInput = false)

    timeout match {
      case None =>
        // blocks, and returns the exit code. Does NOT connect
        // the standard in of the child job to the parent
        val retcode = p.exitValue()
        val (stdout, stderr) = getStds
        if (!allowedReturnCodes.contains(retcode)) {
          logger.error(s"""Execution failed with return code ${retcode}
                          |Command: ${command}
                          |STDOUT: ${stdout}
                          |STDERR: ${stderr}""".stripMargin)
          if (exceptionOnFailure) {
            throw new Exception(s"Error running command ${command}")
          }
        }
        (retcode, stdout, stderr)
      case Some(nSec) =>
        val f = Future(blocking(p.exitValue()))
        try {
          val retcode = Await.result(f, duration.Duration(nSec, "sec"))
          val (stdout, stderr) = getStds
          (retcode, stdout, stderr)
        } catch {
          case _: TimeoutException =>
            p.destroy()
            throw new Exception(s"Timeout exceeded (${nSec} seconds)")
        }
    }
  }

  // Figure how much memory is available.
  //
  // We may need to check the cgroup itself. Not sure how portable that is.
  //val totalAvailableMemoryBytes = Utils.readFileContent("/sys/fs/cgroup/memory/memory.limit_in_bytes").toInt
  def availableMemory: Long = {
    val mbean = ManagementFactory.getOperatingSystemMXBean
      .asInstanceOf[com.sun.management.OperatingSystemMXBean]
    mbean.getTotalPhysicalMemorySize
  }
}
