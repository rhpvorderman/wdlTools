package wdlTools.eval

import wdlTools.syntax.SourceLocation
import wdlTools.types.TypedAbstractSyntax.{HintsSection, RuntimeSection}
import wdlTools.types.{TypedAbstractSyntax => TAT}

/**
  * Unification of runtime and hints sections, to enable accessing runtime attributes in
  * a version-independent manner.
  * @param runtime runtime section
  * @param hints hints section
  */
case class RuntimeAttributes(runtime: Option[Runtime],
                             hints: Option[Hints],
                             defaultValues: Map[String, WdlValues.V]) {
  def getValue(id: String): Option[WdlValues.V] = {
    runtime
      .flatMap(_.getValue(id))
      .orElse(hints.flatMap(_.getValue(id)))
      .orElse(defaultValues.get(id))
  }
}

object RuntimeAttributes {
  def fromTask(
      task: TAT.Task,
      evaluator: Eval,
      ctx: Context,
      defaultValues: Map[String, WdlValues.V] = Map.empty
  ): RuntimeAttributes = {
    create(task.runtime, task.hints, evaluator, ctx, defaultValues, Some(task.loc))
  }

  def create(
      runtimeSection: Option[RuntimeSection],
      hintsSection: Option[HintsSection],
      evaluator: Eval,
      ctx: Context,
      defaultValues: Map[String, WdlValues.V] = Map.empty,
      sourceLocation: Option[SourceLocation] = None
  ): RuntimeAttributes = {
    val runtime = runtimeSection.map(r =>
      Runtime.create(Some(r), ctx, evaluator, runtimeLocation = sourceLocation)
    )
    val hints = hintsSection.map(h => Hints.create(Some(h)))
    RuntimeAttributes(runtime, hints, defaultValues)
  }
}
