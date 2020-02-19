package com.github.foelock.cloudburst.domain

import io.circe.derivation.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class Format(
  protocol: String,
  mime_type: String
) {
  val fileExt = mime_type match {
    case Format.OPUS => "opus"
    case Format.MP3 => "mp3"
    case x => throw new RuntimeException(s"unknown format: $x")
  }
}

object Format {
  val OPUS = "audio/ogg; codecs=\"opus\""
  val MP3 = "audio/mpeg"
  implicit val encoder: Encoder[Format] = deriveEncoder
  implicit val decoder: Decoder[Format] = deriveDecoder
}

case class Transcoding(
  url: String,
  preset: String,
  duration: Long,
  snipped: Boolean,
  format: Format,
  quality: String
)

object Transcoding {


  implicit val encoder: Encoder[Transcoding] = deriveEncoder
  implicit val decoder: Decoder[Transcoding] = deriveDecoder
}

case class Media(
  transcodings: Seq[Transcoding]
) {
  def getTranscoding: Option[Transcoding] = {
    val hlsTranscodings = transcodings.filter(_.format.protocol == "progressive")
    hlsTranscodings.find(_.format.mime_type == Format.OPUS).orElse(hlsTranscodings.find(_.format.mime_type == Format.MP3))
  }
}

object Media {
  implicit val encoder: Encoder[Media] = deriveEncoder
  implicit val decoder: Decoder[Media] = deriveDecoder
}

case class Track(
  media: Media,
  title: String
)

object Track {
  implicit val encoder: Encoder[Track] = deriveEncoder
  implicit val decoder: Decoder[Track] = deriveDecoder
}

case class DownloadUrl(url: String)

object DownloadUrl {
  implicit val encoder: Encoder[DownloadUrl] = deriveEncoder
  implicit val decoder: Decoder[DownloadUrl] = deriveDecoder
}