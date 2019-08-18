package com.github.foelock.cloudburst.domain

import io.circe.derivation.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class MiniUser(
  id: Int,
  kind: String,
  username: String,
  permalink: String,
  last_modified: String,
  uri: String,
  permalink_url: String,
  avatar_url: String
)

object MiniUser {
  implicit val encoder: Encoder[MiniUser] = deriveEncoder
  implicit val decoder: Decoder[MiniUser] = deriveDecoder
}

case class Track(
  id: Long,
  created_at: String, // "2009/08/13 18:30:10 +0000"
  user_id: Long,
  user: MiniUser,
  title: String,
  permalink: String,
  permalink_url: String,
  uri: String,
  sharing: String,
  embeddable_by: String,
  purchase_url: Option[String],
  purchase_title: Option[String],
  artwork_url: String,
  description: String,
  label: Option[MiniUser],
  duration: Long,
  genre: String,
  tag_list: String,
  label_id: Option[Long],
  label_name: Option[String],
  release: Option[Long],
  release_day: Option[Int],
  release_month: Option[Int],
  release_year: Option[Int],
  streamable: Boolean,
  downloadable: Boolean,
  state: String,
  license: String,
  track_type: Option[String],
  waveform_url: String,
  download_url: String,
  stream_url: String,
  video_url: Option[String],
  bpm: Option[Int],
  commentable: Boolean,
  isrc: Option[String],
  key_signature: Option[String],
  comment_count: Long,
  download_count: Long,
  playback_count: Long,
  favoritings_count: Long,
  original_format: String,
  original_content_size: Long,
  user_favorite: Option[Boolean],
  reposts_count: Long,
  policy: String,
  attachments_uri: String,
  monetization_model: String
)

object Track {
  implicit val encoder: Encoder[Track] = deriveEncoder
  implicit val decoder: Decoder[Track] = deriveDecoder
}