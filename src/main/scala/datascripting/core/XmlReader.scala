package datascripting.core

import scala.xml._

object XmlReader {
  def apply(data: Array[Byte]): Elem = xml.XML.loadString(new String(data, "UTF-8"))
  lazy val prettyPrinter = new scala.xml.PrettyPrinter(100, 2)
  def pretty(data: Array[Byte]): String = "\n" + prettyPrinter.format(apply(data))
}

