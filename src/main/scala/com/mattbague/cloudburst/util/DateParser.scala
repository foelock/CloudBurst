package com.mattbague.cloudburst.util
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter._

object DateParser {

  def parseIsoString(isoString: String): LocalDateTime = {
    LocalDateTime.parse(isoString, ISO_DATE_TIME)
  }
}
