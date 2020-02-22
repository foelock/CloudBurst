package com.github.foelock.cloudburst.domain

import io.circe.derivation.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class TrackCollectionEntry(
  kind: String,
  track: Track
)

object TrackCollectionEntry {
  implicit val encoder: Encoder[TrackCollectionEntry] = deriveEncoder
  implicit val decoder: Decoder[TrackCollectionEntry] = deriveDecoder
}

case class TrackCollection(
  collection: Seq[TrackCollectionEntry],
  next_href: Option[String]
)

object TrackCollection {
  implicit val encoder: Encoder[TrackCollection] = deriveEncoder
  implicit val decoder: Decoder[TrackCollection] = deriveDecoder
}