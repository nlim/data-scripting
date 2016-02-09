package datascripting.core

import org.joda.time.DateTime
import org.joda.time.format._

object Formats {
  val DashedFormat = DateTimeFormat.forPattern("yyyy-MM-dd")
  val DottedFormat = DateTimeFormat.forPattern("yyyy.MM.dd")
}

