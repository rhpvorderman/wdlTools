package wdlTools.util

import java.nio.file.Path

import spray.json._

import scala.collection.immutable.TreeMap

object JsUtils {
  def jsFromFile(path: Path): JsValue = {
    FileUtils.readFileContent(path).parseJson
  }

  def jsFromString(json: String): JsValue = {
    json.parseJson
  }

  def jsToString(js: JsValue): String = {
    js.prettyPrint
  }

  def jsToFile(js: JsValue, path: Path): Unit = {
    FileUtils.writeFileContent(path, js.prettyPrint)
  }

  def getFields(js: JsValue, fieldName: Option[String] = None): Map[String, JsValue] = {
    val obj = js match {
      case obj: JsObject => obj
      case other         => other.asJsObject
    }
    fieldName.map(x => obj.fields(x)).getOrElse(obj) match {
      case JsObject(fields) => fields
      case other            => throw new Exception(s"Expected JsObject, got ${other}")
    }
  }

  def getValues(js: JsValue, fieldName: Option[String] = None): Vector[JsValue] = {
    fieldName.map(x => js.asJsObject.fields(x)).getOrElse(js) match {
      case JsArray(values) => values
      case other           => throw new Exception(s"Expected JsArray, got ${other}")
    }
  }

  def getString(js: JsValue, fieldName: Option[String] = None): String = {
    fieldName.map(x => js.asJsObject.fields(x)).getOrElse(js) match {
      case JsString(value) => value
      case JsNumber(value) => value.toString()
      case other           => throw new Exception(s"Expected a string, got ${other}")
    }
  }

  def getInt(js: JsValue, fieldName: Option[String] = None): Int = {
    fieldName.map(x => js.asJsObject.fields(x)).getOrElse(js) match {
      case JsNumber(value) => value.toInt
      case JsString(value) => value.toInt
      case other           => throw new Exception(s"Expected a number, got ${other}")
    }
  }

  // Make a JSON value deterministically sorted.  This is used to
  // ensure that the checksum does not change when maps
  // are ordered in different ways.
  //
  // Note: this does not handle the case of arrays that
  // may have different equivalent orderings.
  def makeDeterministic(jsValue: JsValue): JsValue = {
    jsValue match {
      case JsObject(m: Map[String, JsValue]) =>
        // deterministically sort maps by using a tree-map instead
        // a hash-map
        val mTree = m
          .map { case (k, v) => k -> JsUtils.makeDeterministic(v) }
          .to(TreeMap)
        JsObject(mTree)
      case other =>
        other
    }
  }

  // Replace all special json characters from with a white space.
  def sanitizedString(s: String): JsString = {
    def sanitizeChar(ch: Char): String = ch match {
      case '}'                     => " "
      case '{'                     => " "
      case '$'                     => " "
      case '/'                     => " "
      case '\\'                    => " "
      case '\"'                    => " "
      case '\''                    => " "
      case _ if ch.isLetterOrDigit => ch.toString
      case _ if ch.isControl       => " "
      case _                       => ch.toString
    }

    val sanitized: String = if (s != null) {
      s.flatMap(sanitizeChar)
    } else {
      ""
    }

    JsString(sanitized)
  }
}
