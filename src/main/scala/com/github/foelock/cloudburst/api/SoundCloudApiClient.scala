package com.github.foelock.cloudburst.api

import java.io.{FileOutputStream, InputStream}
import java.net.URL
import java.nio.channels.{Channels, ReadableByteChannel}
import java.nio.file.{FileAlreadyExistsException, Files, Paths}

import com.github.foelock.cloudburst.api.SoundCloudConstants._
import com.github.foelock.cloudburst.domain.{DownloadUrl, Track, Transcoding, User}
import com.github.foelock.cloudburst.util.{JsonUtil, ProgramLocalStorageService}
import io.circe.Decoder

import scala.util.matching.Regex
import scalaj.http._

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
    val request = Http(s"https://soundcloud.com/")
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

  private val baseUrl = "https://api-v2.soundcloud.com"
  private val oldBaseUrl = "https://api.soundcloud.com"

  private def apiRequestFor(resource: String, resourceId: Option[String] = None, queryParams: Map[String, String] = Map()): HttpRequest = {
    val maybeResourceId = resourceId.map(rid => s"/$rid").getOrElse("")
    val path = s"$resource$maybeResourceId"
    val request = Http(s"$baseUrl/$path?client_id=$clientId").params(queryParams)
    println(request.url)
    request
  }

  private def instantiateClientId: Option[String] = {
    val localClientId = programLocalStorageService.current.clientId
    val localClientIdWorks = localClientId.exists { cid =>
      val request = Http(s"$oldBaseUrl/tracks?client_id=$cid") // doesn't use apiUrlFor because instantiation reasons
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
    val request = apiRequestFor("tracks", Some(id.toString))
    val response = request.asString
    parseResponse[Track](response)
  }

  def getTrackByUrl(url: String): Option[Track] = {
    val request = apiRequestFor(resource = "resolve", queryParams = Map("url" -> url))
    val response = request.asString
    parseResponse[Track](response)
  }

  def getUserById(id: Long): Option[User] = {
    val request = apiRequestFor("users", Some(id.toString))

    val response = request.asString

    println(response.body)
    None
  }

  // todo: set metadata tags
  def downloadTrack(track: Track): Unit = {
    for {
      transcoding <- track.media.getTranscoding
      downloadUrl <- {
        val transcodeResponse = Http(s"${transcoding.url}?client_id=$clientId").asString
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

    try {
      dlStream = Some(new URL(url).openStream())
      rbc = Some(Channels.newChannel(dlStream.get))
      val dlFile = Files.createFile(Paths.get(downloadPath.toString, s"${track.title}.$fileExt")).toFile
      println(s"downloading to ${dlFile.getAbsolutePath}")
      fos = Some(new FileOutputStream(dlFile))
      fos.get.getChannel.transferFrom(rbc.get, 0, Long.MaxValue)
      println(s"successfully downloaded ${dlFile.getAbsolutePath} ")
    } catch {
      case _: FileAlreadyExistsException =>
        println("file already exists. skipping")
      case exception: Exception =>
        println(s"error downloading ${track.title}: $exception")
    } finally {
      fos.foreach(_.close())
      rbc.foreach(_.close())
      dlStream.foreach(_.close())
    }
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

  val FALLBACK_CLIENT_ID = "1CaOnsDIpR4VuCkgEPDmgAWP2g9lyG2S"
}