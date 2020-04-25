package com.mattbague.cloudburst.util

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import io.circe.derivation.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class ProgramLocalStorage(
  clientId: Option[String],
  userId: Option[Long]
)

object ProgramLocalStorage {
  implicit val encoder: Encoder[ProgramLocalStorage] = deriveEncoder
  implicit val decoder: Decoder[ProgramLocalStorage] = deriveDecoder
}

class ProgramLocalStorageService(userDir: String) {

  private lazy val localStoragePath = {
    val path = Paths.get(s"$userDir/.cloudburst/data.json")
    if (!Files.exists(path)) {
      Files.createDirectories(path.getParent)
      Files.createFile(path)
    }

    path
  }

  private var _current = {
    load().getOrElse(ProgramLocalStorage(None, None))
  }

  def current: ProgramLocalStorage = _current

  def store(programData: ProgramLocalStorage): Unit = {
    try {
      Files.write(localStoragePath, JsonUtil.toJson(programData, pretty = true).getBytes(StandardCharsets.UTF_8))
      _current = programData
    } catch {
      case e: Exception => println(e.getMessage)
    }
  }

  def shutdown(): Unit = {
    store(_current)
  }

  private def load(): Option[ProgramLocalStorage] = {
    try {
      val fileContents = new String(Files.readAllBytes(localStoragePath))
      if (fileContents.nonEmpty) {
        val storage = JsonUtil.fromJson[ProgramLocalStorage](fileContents)
        _current = storage
        Some(storage)
      }
      else None
    } catch {
      case e: Exception =>
        println(e.getMessage)
        None
    }
  }
}
