package com.github.foelock.cloudburst.util

import io.circe.parser.parse
import io.circe.{Decoder, Encoder, Printer, _}

object JsonUtil {

  val printer: Printer = Printer.noSpaces.copy(dropNullValues = true)
  val prettyPrinter: Printer = Printer.spaces2.copy(dropNullValues = true)

  def toJson[T](obj: T, pretty: Boolean = false)(implicit encoder: Encoder[T]): String = {
    if (pretty) encoder(obj).pretty(prettyPrinter) else encoder(obj).pretty(printer)

  }

  def fromJson[T](json: String)(implicit decoder: Decoder[T]): T = {
    parse(json) match {
      case Left(err: io.circe.Error) => throw new RuntimeException(s"Error parsing json $json. \n${err.getMessage()}")
      case Right(parsed: Json) => parsed.as[T] match {
        case Left(err) => throw new RuntimeException(s"Error deserializing json $json. \n${err.getMessage()}")
        case Right(result) => result
      }
    }
  }

}