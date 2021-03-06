package wdlTools.generators.code

import wdlTools.generators.code.Indenting.Indenting
import wdlTools.generators.code.Spacing.Spacing
import wdlTools.generators.code.Wrapping.Wrapping
import wdlTools.syntax.{Comment, CommentMap}

import scala.collection.mutable

object BaseWdlFormatter {

  /**
    * An element that (potentially) spans multiple source lines.
    */
  trait Multiline extends Ordered[Multiline] {
    def line: Int

    def endLine: Int

    lazy val lineRange: Range = line to endLine

    override def compare(that: Multiline): Int = {
      line - that.line match {
        case 0     => endLine - that.endLine
        case other => other
      }
    }
  }

  /**
    * An element that can be formatted by a Formatter.
    * Column positions are 1-based and end-exclusive
    */
  trait Span extends Sized with Multiline {

    /**
      * The first column in the span.
      */
    def column: Int

    /**
      * The last column in the span.
      */
    def endColumn: Int
  }

  object Span {
    // indicates the last token on a line
    val TERMINAL: Int = Int.MaxValue
  }

  /**
    * Marker trait for atomic Spans - those that format themselves via their
    * toString method. An atomic Span is always on a single source line (i.e.
    * `line` == `endLine`).
    */
  trait Atom extends Span {
    override def endLine: Int = line

    def toString: String
  }

  /**
    * A Span that contains other Spans and knows how to format itself.
    */
  trait Composite extends Span {

    /**
      * Format the contents of the composite. The `lineFormatter` passed to this method
      * must have `isLineBegun == true` on both entry and exit.
      *
      * @param lineFormatter the lineFormatter
      */
    def formatContents(lineFormatter: LineFormatter): Unit

    /**
      * Whether this Composite is a section, which may contain full-line comments.
      */
    def isSection: Boolean = false
  }

  class LineFormatter(
      comments: CommentMap,
      indenting: Indenting = Indenting.IfNotIndented,
      indentStep: Int = 2,
      initialIndentSteps: Int = 0,
      indentation: String = " ",
      wrapping: Wrapping = Wrapping.AsNeeded,
      maxLineWidth: Int = 100,
      private val lines: mutable.Buffer[String],
      private val currentLine: mutable.StringBuilder,
      private val currentLineComments: mutable.Map[Int, String] = mutable.HashMap.empty,
      private var currentIndentSteps: Int = 0,
      private var currentSpacing: Spacing = Spacing.On,
      private val lineBegun: MutableHolder[Boolean] = MutableHolder[Boolean](false),
      private val sections: mutable.Buffer[Multiline] = mutable.ArrayBuffer.empty,
      private val currentSourceLine: MutableHolder[Int] = MutableHolder[Int](0),
      private val skipNextSpace: MutableHolder[Boolean] = MutableHolder[Boolean](false)
  ) {

    private val commentStart = "^#+".r
    private val whitespace = "[ \t\n\r]+".r

    /**
      * Derive a new LineFormatter with the current state modified by the specified parameters.
      * @param increaseIndent whether to incerase the indent by one step
      * @param newIndenting new value for `indenting`
      * @param newSpacing new value for `spacing`
      * @param newWrapping new value for `wrapping`
      * @return
      */
    def derive(increaseIndent: Boolean = false,
               continuing: Boolean = false,
               newIndenting: Indenting = indenting,
               newSpacing: Spacing = currentSpacing,
               newWrapping: Wrapping = wrapping): LineFormatter = {
      val indentSteps = if (continuing) currentIndentSteps else initialIndentSteps
      val newInitialIndentSteps = indentSteps + (if (increaseIndent) 1 else 0)
      val newCurrentIndentSteps =
        if (increaseIndent && newInitialIndentSteps > currentIndentSteps) {
          newInitialIndentSteps
        } else {
          currentIndentSteps
        }
      new LineFormatter(comments,
                        newIndenting,
                        indentStep,
                        newInitialIndentSteps,
                        indentation,
                        newWrapping,
                        maxLineWidth,
                        lines,
                        currentLine,
                        currentLineComments,
                        newCurrentIndentSteps,
                        newSpacing,
                        lineBegun,
                        sections,
                        currentSourceLine,
                        skipNextSpace)
    }

    def isLineBegun: Boolean = lineBegun.value

    def atLineStart: Boolean = {
      currentLine.length <= (currentIndentSteps * indentStep)
    }

    def getIndent(changeSteps: Int = 0): String =
      indentation * ((currentIndentSteps + changeSteps) * indentStep)

    def lengthRemaining: Int = {
      if (atLineStart) {
        maxLineWidth
      } else {
        maxLineWidth - currentLine.length
      }
    }

    def beginSection(section: Multiline): Unit = {
      sections.append(section)
      currentSourceLine.value = section.line
    }

    def endSection(section: Multiline): Unit = {
      require(sections.nonEmpty)
      val popSection = sections.last
      if (section != popSection) {
        throw new Exception(s"Ending the wrong section: ${section} != ${popSection}")
      }
      maybeAppendFullLineComments(popSection, isSectionEnd = true)
      sections.remove(sections.size - 1)
    }

    def emptyLine(): Unit = {
      require(!isLineBegun)
      lines.append("")
    }

    def beginLine(): Unit = {
      require(!isLineBegun)
      currentLine.append(getIndent())
      lineBegun.value = true
    }

    private def dent(indenting: Indenting): Unit = {
      indenting match {
        case Indenting.Always =>
          currentIndentSteps += 1
        case Indenting.IfNotIndented if currentIndentSteps == initialIndentSteps =>
          currentIndentSteps += 1
        case Indenting.Dedent if currentIndentSteps > initialIndentSteps =>
          currentIndentSteps -= 1
        case Indenting.Reset =>
          currentIndentSteps = initialIndentSteps
        case Indenting.Never =>
          currentIndentSteps = 0
        case _ => ()
      }
    }

    def endLine(continue: Boolean = false): Unit = {
      require(isLineBegun)
      if (currentLineComments.nonEmpty) {
        if (!atLineStart) {
          currentLine.append("  ")
        }
        currentLine.append(Symbols.Comment)
        currentLine.append(" ")
        currentLine.append(
            currentLineComments.toVector.sortWith((a, b) => a._1 < b._1).map(_._2).mkString(" ")
        )
        currentLineComments.clear()
      }
      if (!atLineStart) {
        lines.append(currentLine.toString)
        if (continue) {
          dent(indenting)
        } else {
          dent(Indenting.Reset)
        }
      }
      currentLine.clear()
      lineBegun.value = false
      skipNextSpace.value = false
    }

    private def trimComment(comment: Comment): (String, Int, Boolean) = {
      val text = comment.value.trim
      val hashes = commentStart.findFirstIn(text)
      if (hashes.isEmpty) {
        throw new Exception("Expected comment to start with '#'")
      }
      val preformatted = hashes.get.startsWith(Symbols.PreformattedComment)
      val rawText = text.substring(hashes.get.length)
      (if (preformatted) rawText else rawText.trim, comment.loc.line, preformatted)
    }

    /**
      * Append one or more full-line comments.
      * @param ml the Multiline before which comments should be added
      * @param isSectionEnd if true, comments are added between the previous source line and
      * the end of the section; otherwise comments are added between the previous source
      * line and the beginning of `ml`
      */
    private def maybeAppendFullLineComments(ml: Multiline, isSectionEnd: Boolean = false): Unit = {
      val beforeLine = if (isSectionEnd) ml.endLine else ml.line
      require(beforeLine >= currentSourceLine.value)
      require(beforeLine <= sections.last.endLine)

      val lineComments = comments.filterWithin((currentSourceLine.value + 1) until beforeLine)

      if (lineComments.nonEmpty) {
        val sortedComments = lineComments.toSortedVector
        val beforeDist = sortedComments.head.loc.line - currentSourceLine.value
        val afterDist = beforeLine - sortedComments.last.loc.endLine

        if (beforeDist > 1) {
          lines.append("")
        }

        var prevLine = 0
        var preformatted = false

        sortedComments.map(trimComment).foreach {
          case (trimmed, curLine, curPreformatted) =>
            if (prevLine > 0 && curLine > prevLine + 1) {
              endLine()
              emptyLine()
              beginLine()
            } else if (!preformatted && curPreformatted) {
              endLine()
              beginLine()
            }
            if (curPreformatted) {
              currentLine.append(Symbols.PreformattedComment)
              currentLine.append(" ")
              currentLine.append(trimmed)
              endLine()
              beginLine()
            } else {
              if (atLineStart) {
                currentLine.append(Symbols.Comment)
              }
              if (lengthRemaining >= trimmed.length + 1) {
                currentLine.append(" ")
                currentLine.append(trimmed)
              } else {
                whitespace.split(trimmed).foreach { token =>
                  // we let the line run over for a single token that is longer than
                  // the max line length (i.e. we don't try to hyphenate)
                  if (!atLineStart && lengthRemaining < token.length + 1) {
                    endLine()
                    beginLine()
                    currentLine.append(Symbols.Comment)
                  }
                  currentLine.append(" ")
                  currentLine.append(token)
                }
              }
            }
            prevLine = curLine
            preformatted = curPreformatted
        }

        endLine()

        if (afterDist > 1) {
          emptyLine()
        }

        beginLine()
      }

      currentSourceLine.value = ml match {
        case c: Composite if c.isSection && !isSectionEnd => ml.line
        case _                                            => ml.endLine
      }
    }

    /**
      * Add to `currentLineComments` any end-of-line comments associated with any of
      * `span`'s source lines.
      */
    private def maybeAddInlineComments(atom: Atom): Unit = {
      val range = atom match {
        case m: Multiline => m.lineRange
        case s            => s.line to s.line
      }
      currentLineComments ++= comments
        .filterWithin(range)
        .toSortedVector
        .filter(comment => !currentLineComments.contains(comment.loc.line))
        .map(comment => comment.loc.line -> trimComment(comment)._1)
    }

    def addInlineComment(line: Int, text: String): Unit = {
      require(!currentLineComments.contains(line))
      currentLineComments(line) = text
    }

    def append(span: Span, continue: Boolean = true): Unit = {
      require(isLineBegun)

      if (atLineStart && sections.nonEmpty) {
        maybeAppendFullLineComments(span)
      }

      if (wrapping == Wrapping.Always) {
        endLine(continue = continue)
        beginLine()
      } else {
        val addSpace = currentLine.nonEmpty &&
          currentSpacing == Spacing.On &&
          !skipNextSpace.value &&
          !currentLine.last.isWhitespace &&
          currentLine.last != indentation.last
        if (wrapping != Wrapping.Never && lengthRemaining < span.length + (if (addSpace) 1 else 0)) {
          endLine(continue = continue)
          beginLine()
        } else if (addSpace) {
          currentLine.append(" ")
        }
      }

      span match {
        case c: Composite =>
          if (c.isSection) {
            beginSection(c)
          }
          c.formatContents(this)
          if (c.isSection) {
            endSection(c)
          }
        case a: Atom =>
          currentLine.append(a.toString)
          if (skipNextSpace.value) {
            skipNextSpace.value = false
          }
          if (a.line > currentSourceLine.value) {
            currentSourceLine.value = a.line
          }
          maybeAddInlineComments(a)
        case other =>
          throw new Exception(s"Span ${other} must implement either Atom or Delegate trait")
      }
    }

    def appendAll(spans: Vector[Span], continue: Boolean = true): Unit = {
      spans.foreach(span => append(span, continue))
    }

    // TODO: these two methods are a hack - they are currently needed to handle the case of
    //  printing a prefix followed by any number of spans followed by a suffix, and suppress
    //  the space after the prefix and before the suffix. Ideally, this would be handled by
    //  `append` using a different `Spacing` value.

    def appendPrefix(prefix: Span): Unit = {
      append(prefix)
      skipNextSpace.value = true
    }

    def appendSuffix(suffix: Span): Unit = {
      skipNextSpace.value = true
      append(suffix)
    }

    def toVector: Vector[String] = {
      lines.toVector
    }
  }

  object LineFormatter {
    def apply(comments: CommentMap,
              indenting: Indenting = Indenting.IfNotIndented,
              indentStep: Int = 2,
              initialIndentSteps: Int = 0,
              indentation: String = " ",
              wrapping: Wrapping = Wrapping.AsNeeded,
              maxLineWidth: Int = 100): LineFormatter = {
      val lines: mutable.Buffer[String] = mutable.ArrayBuffer.empty
      val currentLine: mutable.StringBuilder = new StringBuilder(maxLineWidth)
      new LineFormatter(comments,
                        indenting,
                        indentStep,
                        initialIndentSteps,
                        indentation,
                        wrapping,
                        maxLineWidth,
                        lines,
                        currentLine)
    }
  }
}
