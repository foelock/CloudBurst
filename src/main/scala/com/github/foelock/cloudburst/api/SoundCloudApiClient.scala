package com.github.foelock.cloudburst.api

import java.io.{File, FileOutputStream, InputStream}
import java.net.URL
import java.nio.channels.{Channels, ReadableByteChannel}
import java.nio.file.{FileAlreadyExistsException, Files, Paths}

import com.github.foelock.cloudburst.api.SoundCloudConstants._
import com.github.foelock.cloudburst.domain._
import com.github.foelock.cloudburst.util.{JsonUtil, ProgramLocalStorageService}
import com.mpatric.mp3agic.{ID3v24Tag, Mp3File}
import io.circe.Decoder
import scalaj.http._

import scala.concurrent.duration._
import scala.util.matching.Regex

class SoundCloudApiClient(
  programLocalStorageService: ProgramLocalStorageService,
  baseDownloadPath: String,
  clientIdOverride: Option[String] = None) {

  private lazy val clientId = clientIdOverride.orElse(instantiateClientId).getOrElse(FALLBACK_CLIENT_ID)

  private val downloadPath = {
    val path = Paths.get(baseDownloadPath)
    if (!Files.exists(path)) {
      Files.createDirectories(path)
    }
    path
  }

  private def fetchNewClientId(): Option[String] = {
    val request = Http(SoundCloudConstants.HOME_PAGE)
    val response = request.asString

    val jsBundleUrls = JsBundleRegex.findAllIn(response.body)

    jsBundleUrls.map { jsBundleUrl =>
      println(s"checking $jsBundleUrl...")
      val jsBundle = Http(jsBundleUrl).asString.body
      val maybeClientId = ClientIdRegex.findFirstMatchIn(jsBundle).map(_.group(1))
      if (maybeClientId.isDefined) {
        println(s"found a client id! ${maybeClientId.get}")
        programLocalStorageService.store(programLocalStorageService.current.copy(clientId = maybeClientId))
        maybeClientId
      } else {
        Thread.sleep(3000)
        None
      }
    }.find(_.isDefined).flatten
  }

  private def apiRequestFor(urlOrPath: String, queryParams: Map[String, String] = Map()): HttpRequest = {
    val alreadyHasBaseUrl = urlOrPath.startsWith(API_V2_URL)
    val url = if (alreadyHasBaseUrl) urlOrPath else s"$API_V2_URL/$urlOrPath"
    val request = Http(url).params(queryParams + ("client_id" -> clientId))
    println(request.url)
    request
  }

  private def instantiateClientId: Option[String] = {
    val localClientId = programLocalStorageService.current.clientId
    val localClientIdWorks = localClientId.exists { cid =>
      val request = Http(s"$API_V1_URL/tracks?client_id=$cid") // doesn't use apiUrlFor because instantiation reasons
      val response = request.asString
      response.code == 200
    }

    if (localClientIdWorks) {
      println("local client id works")
      localClientId
    } else {
      println("fetching new client id...")
      fetchNewClientId()
    }
  }

  def getTrackById(id: Long = 525671871): Option[Track] = {
    val request = apiRequestFor(s"tracks/$id")
    val response = request.asString
    parseResponse[Track](response)
  }

  def getTrackByUrl(url: String): Option[Track] = {
    val request = apiRequestFor("resolve", queryParams = Map("url" -> url))
    val response = request.asString
    parseResponse[Track](response)
  }

  def getUserById(id: Long): Option[User] = {
    val request = apiRequestFor(s"users/$id")

    val response = request.asString

    println(response.body)
    None
  }

  def getUserLikesById(userId: Long): Seq[Track] = {
    val initialRequest = apiRequestFor(
      s"users/$userId/track_likes",
      queryParams = Map("limit" -> "24")
    )

    var count = 0

    var currentTrackCollection = parseResponse[TrackCollection](initialRequest.asString)
    var tracks = Seq.empty[Track]
    while (currentTrackCollection.exists(_.next_href.isDefined) && count < 3) {
      val current = currentTrackCollection.get
      tracks ++= current.collection.map(_.track)
      count += 1
      Thread.sleep(API_CALL_DELAY_MS)

      currentTrackCollection = parseResponse[TrackCollection](apiRequestFor(current.next_href.get).asString)
    }

    tracks
  }

  def downloadTrack(track: Track): Unit = {
    for {
      transcoding <- track.media.getTranscoding
      downloadUrl <- {
        val transcodeResponse = Http(transcoding.url).param("client_id", clientId).asString
        parseResponse[DownloadUrl](transcodeResponse)
      }
    } yield {
      _downloadTrack(downloadUrl.url, track, transcoding)
    }
  }

  private def _downloadTrack(url: String, track: Track, transcoding: Transcoding): Unit = {
    var dlStream: Option[InputStream] = None
    var rbc: Option[ReadableByteChannel] = None
    var fos: Option[FileOutputStream] = None
    val fileExt = transcoding.format.fileExt

    val desiredFilePath = downloadPath.resolve(s"${track.title}.$fileExt")
    if (Files.exists(desiredFilePath)) {
      println(s"File ${desiredFilePath.toString} already exists. skipping")
    } else {
      val dlFile = Files.createFile(desiredFilePath).toFile
      val wasWritten = try {
        dlStream = Some(new URL(url).openStream())
        rbc = Some(Channels.newChannel(dlStream.get))
        println(s"downloading to ${dlFile.getAbsolutePath}")
        fos = Some(new FileOutputStream(dlFile))
        fos.get.getChannel.transferFrom(rbc.get, 0, Long.MaxValue)
        println(s"successfully downloaded ${dlFile.getAbsolutePath} ")
        true
      } catch {
        case _: FileAlreadyExistsException =>
          println("file already exists. skipping")
          false
        case exception: Exception =>
          println(s"error downloading ${track.title}: $exception")
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
  }

  private def setId3V2Tags(file: File, track: Track): Unit = {
    val stagingFilePath = downloadPath.resolve(s".tmp_${file.getName}")
    println(s"copying to ${stagingFilePath.toString}")
    Files.copy(file.toPath, stagingFilePath)
    val mp3File = new Mp3File(stagingFilePath)
    val tag = new ID3v24Tag()

    val albumImageResponse = Http(track.artwork_url).asBytes
    val mimeType = albumImageResponse.contentType.getOrElse("image/jpeg")
    val albumImage = albumImageResponse.body

    tag.setArtist(track.user.username)
    tag.setTitle(track.title)
    tag.setRecordingTime(track.createdAt.toString)
    tag.setArtistUrl(track.user.permalink_url)
    tag.setAudioSourceUrl(track.permalink_url)
    tag.setUrl(track.permalink_url)
    tag.setAlbumImage(albumImage, mimeType)
    tag.setGenreDescription(track.genre)

    mp3File.setId3v2Tag(tag)

    Files.delete(file.toPath)
    mp3File.save(file.getAbsolutePath)
    Files.delete(stagingFilePath)
  }

  private def parseResponse[T](response: HttpResponse[String])(implicit decoder: Decoder[T]): Option[T] = {
    try {
      Some(JsonUtil.fromJson[T](response.body))
    } catch {
      case e: Exception =>
        println(s"error parsing: ${response.body}", e)
        None
    }
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

  val API_DOWNLOAD_DELAY_MS: Long = 20.seconds.toMillis
}