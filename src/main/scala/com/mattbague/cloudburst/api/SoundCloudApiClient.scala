package com.mattbague.cloudburst.api

import java.io.{File, FileOutputStream, InputStream}
import java.net.URL
import java.nio.channels.{Channels, ReadableByteChannel}
import java.nio.file.{FileAlreadyExistsException, Files, Path, Paths}

import com.mattbague.cloudburst.api.SoundCloudConstants._
import com.mattbague.cloudburst.domain._
import com.mattbague.cloudburst.util.{JsonUtil, ProgramLocalStorageService}
import com.mpatric.mp3agic.{ID3v24Tag, Mp3File}
import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import scalaj.http._

import scala.concurrent.duration._
import scala.util.matching.Regex

class SoundCloudApiClient(
  programLocalStorageService: ProgramLocalStorageService,
  baseDownloadPath: String,
  clientIdOverride: Option[String] = None) extends LazyLogging {

  private lazy val clientId = clientIdOverride.orElse(instantiateClientId).getOrElse(FALLBACK_CLIENT_ID)

  private val downloadPath = {
    val path = Paths.get(baseDownloadPath)
    if (!Files.exists(path)) {
      Files.createDirectories(path)
    }
    path
  }

  private def fetchNewClientId(): Option[String] = {
    val response = apiGet(Http(SoundCloudConstants.HOME_PAGE))

    val jsBundleUrls = JsBundleRegex.findAllIn(response.body).toSeq.reverse

    logger.info(s"found ${jsBundleUrls.size} js bundles to check")

    jsBundleUrls.toStream.map { jsBundleUrl =>
      logger.info(s"checking $jsBundleUrl...")
      val jsBundle = apiGet(Http(jsBundleUrl)).body
      val maybeClientId = ClientIdRegex.findFirstMatchIn(jsBundle).map(_.group(1))
      if (maybeClientId.isDefined) {
        logger.info(s"found a client id! ${maybeClientId.get}")
        programLocalStorageService.store(programLocalStorageService.current.copy(clientId = maybeClientId))
        maybeClientId
      } else {
        logger.info(s"client id not found, checking next js bundle")
        None
      }
    }.find(_.isDefined).flatten
  }

  private def apiRequestFor(urlOrPath: String, queryParams: Map[String, String] = Map()): HttpRequest = {
    val alreadyHasBaseUrl = urlOrPath.startsWith(API_V2_URL)
    val url = if (alreadyHasBaseUrl) urlOrPath else s"$API_V2_URL/$urlOrPath"
    val request = Http(url).params(queryParams + ("client_id" -> clientId))
    logger.info(request.url)
    request
  }

  private def instantiateClientId: Option[String] = {
    val localClientId = programLocalStorageService.current.clientId
    val localClientIdWorks = localClientId.exists { cid =>
      val response = apiGet(Http(s"$API_V2_URL/featured_tracks/front?client_id=$cid")) // doesn't use apiUrlFor because instantiation reasons
      response.code == 200
    }

    if (localClientIdWorks) {
      logger.info("local client id works")
      localClientId
    } else {
      logger.info("fetching new client id...")
      fetchNewClientId()
    }
  }

  def getTrackById(id: Long = 525671871): Option[Track] = {
    val response = apiGet(apiRequestFor(s"tracks/$id"))
    parseResponse[Track](response).map(sanitizeTrackTitle)
  }

  def getTrackByUrl(url: String): Option[Track] = {
    val response = apiGet(apiRequestFor("resolve", queryParams = Map("url" -> url)))
    parseResponse[Track](response).map(sanitizeTrackTitle)
  }

  def getUserById(id: Long): Option[User] = {
    val response = apiGet(apiRequestFor(s"users/$id"))

    logger.info(response.body)
    None
  }

  def getUserLikesById(userId: Long): Seq[Track] = {
    val initialResponse = apiGet(
      apiRequestFor(
        urlOrPath = s"users/$userId/track_likes",
        queryParams = Map(
          "limit" -> "24",
          "offset" -> "0",
          "linked_partitioning" -> "1",
          "app_locale" -> "en"
        )
      ),
      5000
    )

    var currentTrackCollection = parseResponse[TrackCollection](initialResponse)
    var tracks = Seq.empty[Track]
    while (currentTrackCollection.exists(_.next_href.isDefined)) {
      val current = currentTrackCollection.get
      tracks ++= current.collection.map(_.track)

      currentTrackCollection = parseResponse[TrackCollection](apiGet(apiRequestFor(current.next_href.get), 5000))
    }

    logger.info(s"Found ${tracks.size} liked tracks")

    tracks.map(sanitizeTrackTitle)
  }

  private def doesFileAlreadyExist(path: Path): Boolean = {
    val exists = Files.exists(path)
    if (exists) logger.info(s"File ${path.toString} already exists. skipping")
    exists
  }

  def downloadTrack(track: Track): Unit = {
    logger.info(s"[START] Download: ${track.title}")
    for {
      transcoding <- track.media.getTranscoding
      fileExt = transcoding.format.fileExt
      downloadTarget = downloadPath.resolve(s"${track.title}.$fileExt")
      if !doesFileAlreadyExist(downloadTarget)
      downloadUrl <- {
        val transcodeResponse = apiGet(Http(transcoding.url).param("client_id", clientId))
        parseResponse[DownloadUrl](transcodeResponse)
      }
    } yield {
      _downloadTrack(downloadUrl.url, track, transcoding, downloadTarget)
    }
    logger.info(s"[END] Download: ${track.title}")
  }

  private def _downloadTrack(url: String, track: Track, transcoding: Transcoding, downloadTarget: Path): Unit = {
    var dlStream: Option[InputStream] = None
    var rbc: Option[ReadableByteChannel] = None
    var fos: Option[FileOutputStream] = None

    val dlFile = Files.createFile(downloadTarget).toFile
    val wasWritten = try {
      dlStream = Some(new URL(url).openStream())
      rbc = Some(Channels.newChannel(dlStream.get))
      logger.info(s"Downloading to file: ${dlFile.getAbsolutePath}")
      fos = Some(new FileOutputStream(dlFile))
      fos.get.getChannel.transferFrom(rbc.get, 0, Long.MaxValue)
      logger.info(s"Download success: ${dlFile.getAbsolutePath} ")
      true
    } catch {
      case _: FileAlreadyExistsException =>
        logger.info(s"${dlFile.getAbsolutePath} already exists. skipping")
        true
      case exception: Exception =>
        logger.error(s"Error downloading ${track.title}", exception)
        false
    } finally {
      fos.foreach(_.close())
      rbc.foreach(_.close())
      dlStream.foreach(_.close())
    }

    if (wasWritten) {
      setId3V2Tags(dlFile, track)
    }
  }

  private def setId3V2Tags(file: File, track: Track): Unit = {
    logger.info(s"Tagging: ${file.getAbsolutePath}")
    val stagingFilePath = downloadPath.resolve(s".tmp_${file.getName}")
    Files.copy(file.toPath, stagingFilePath)

    val mp3File = new Mp3File(stagingFilePath)
    val tag = new ID3v24Tag()

    tag.setArtist(track.user.username)
    tag.setTitle(track.title)
    tag.setRecordingTime(track.createdAt.toString)
    tag.setArtistUrl(track.user.permalink_url)
    tag.setAudioSourceUrl(track.permalink_url)
    tag.setUrl(track.permalink_url)

    track.genre.foreach(tag.setGenreDescription)

    track.artwork_url.foreach { artworkUrl =>
      val hqUrl = artworkUrl.replace("large", "t500x500")
      val hqImageResponse = apiGetBytes(Http(hqUrl))

      val albumImageResponse =
        if (hqImageResponse.code == 200) {
          logger.info(s"Found HQ album artwork: $hqUrl")
          hqImageResponse
        }
        else {
          logger.info(s"No HQ album artwork, falling back to: $artworkUrl")
          apiGetBytes(Http(hqUrl))
        }

      val mimeType = albumImageResponse.contentType.getOrElse("image/jpeg")
      val albumImage = albumImageResponse.body
      tag.setAlbumImage(albumImage, mimeType)
    }

    mp3File.setId3v2Tag(tag)

    Files.delete(file.toPath)
    mp3File.save(file.getAbsolutePath)

    Files.delete(stagingFilePath)

    logger.info(s"Finished tagging: ${file.getAbsolutePath}")
  }

  private def parseResponse[T](response: HttpResponse[String])(implicit decoder: Decoder[T]): Option[T] = {
    try {
      Some(JsonUtil.fromJson[T](response.body))
    } catch {
      case e: Exception =>
        logger.error(s"error parsing: ${response.body}", e)
        None
    }
  }

  private def sanitizeTrackTitle(track: Track): Track = {
    track.copy(
      title = track.title.replaceAll("[\\\\/:*?\"<>|]", "_").trim
    )
  }

  private def apiGet(request: HttpRequest, apiDelayMs: Long = API_CALL_DELAY_MS): HttpResponse[String] = {
    val response = request.asString
    logger.info(s"In quiet mode for ${apiDelayMs}ms")
    Thread.sleep(apiDelayMs)
    response
  }

  private def apiGetBytes(request: HttpRequest): HttpResponse[Array[Byte]] = {
    val response = request.asBytes
    logger.info(s"In quiet mode for ${API_CALL_DELAY_MS}ms")
    Thread.sleep(API_CALL_DELAY_MS)
    response
  }
}

object SoundCloudConstants {
  val JsBundleRegex: Regex = "https://a-v2.sndcdn.com/assets/[0-9]+-.*.js".r
  val ClientIdRegex: Regex = """client_id:"([A-Za-z0-9]+)"""".r

  val HOME_PAGE = "https://soundcloud.com/"

  val API_V2_URL = "https://api-v2.soundcloud.com"
  val API_V1_URL = "https://api.soundcloud.com"

  val FALLBACK_CLIENT_ID = "1CaOnsDIpR4VuCkgEPDmgAWP2g9lyG2S"

  val API_CALL_DELAY_MS: Long = 10.seconds.toMillis
}