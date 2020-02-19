package com.github.foelock.cloudburst.api

import java.io.{FileOutputStream, InputStream}
import java.net.{URL, URLEncoder}
import java.nio.channels.{Channels, ReadableByteChannel}
import java.nio.file.{FileAlreadyExistsException, Files, Paths}

import cats.Id
import com.github.foelock.cloudburst.api.SoundCloudConstants._
import com.github.foelock.cloudburst.domain.{DownloadUrl, Track, Transcoding, User}
import com.github.foelock.cloudburst.util.{JsonUtil, ProgramLocalStorageService}
import sttp.client._
import io.circe.Decoder
import sttp.model.{StatusCode, Uri}

import scala.util.matching.Regex

class SoundCloudApiClient(
  programLocalStorageService: ProgramLocalStorageService,
  baseDownloadPath: String,
  clientIdOverride: Option[String] = None) {

  //  private implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()
  private implicit val backend = HttpURLConnectionBackend()

  private lazy val clientId = clientIdOverride.orElse(instantiateClientId).getOrElse(FALLBACK_CLIENT_ID)

  private val downloadPath = {
    val path = Paths.get(baseDownloadPath)
    if (!Files.exists(path)) {
      Files.createDirectories(path)
    }
    path
  }

  private type ResponseType = Identity[Response[Either[String, String]]]

  private def extractBodyRaw(response: ResponseType): String = {
    response.body match {
      case Left(value) => throw new RuntimeException(s"Error parsing body: ${value}")
      case Right(value) => value
    }
  }

  private def fetchNewClientId(): Option[String] = {
    val request = basicRequest.get(uri"https://soundcloud.com/")
    val response: Identity[Response[Either[String, String]]] = request.send()

    val jsBundleUrls = JsBundleRegex.findAllIn(extractBodyRaw(response))

    jsBundleUrls.map { jsBundleUrl =>
      println(s"checking ${jsBundleUrl}...")
      val jsBundle = extractBodyRaw(basicRequest.get(uri"$jsBundleUrl").send())
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

  private def apiUrlFor(resource: String, resourceId: Option[String] = None, queryParams: Map[String, String] = Map()): Uri = {
    val maybeResourceId = resourceId.map(rid => s"/$rid").getOrElse("")
    val path = s"$resource$maybeResourceId"
    val uri = uri"$baseUrl/$path?$queryParams&client_id=$clientId"
    uri
  }

  private def instantiateClientId: Option[String] = {
    val localClientId = programLocalStorageService.current.clientId
    val localClientIdWorks = localClientId.exists { cid =>
      val request = basicRequest.get(uri"https://api.soundcloud.com/tracks?client_id=$cid") // doesn't use apiUrlFor because instantiation reasons
      val response = request.send()
      response.code == StatusCode.Ok
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
    val request = basicRequest.get(apiUrlFor("tracks", Some(id.toString)))
    val response = request.send()
    parseResponse[Track](response)
  }

  def getTrackByUrl(url: String): Option[Track] = {
    val request = basicRequest.get(apiUrlFor(resource = "resolve", queryParams = Map("url" -> url)))
    val response = request.send()
    parseResponse[Track](response)
  }

  def getUserById(id: Long): Option[User] = {
    val request = basicRequest.get(apiUrlFor("users", Some(id.toString)))

    val response = request.send()

    println(extractBodyRaw(response))
    None
  }

  // todo: set metadata tags
  def downloadTrack(track: Track): Unit = {
    for {
      transcoding <- track.media.getTranscoding
      downloadUrl <- {
        val transcodeResponse = basicRequest.get(uri"${transcoding.url}?client_id=$clientId").send()
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
      val dlFile = Files.createFile(Paths.get(downloadPath.toString, s"${track.title}.${fileExt}")).toFile
      println(s"downloading to ${dlFile.getAbsolutePath}")
      fos = Some(new FileOutputStream(dlFile))
      fos.get.getChannel.transferFrom(rbc.get, 0, Long.MaxValue)
      println(s"successfully downloaded ${dlFile.getAbsolutePath} ")
    } catch {
      case _: FileAlreadyExistsException =>
        println("file already exists. skipping")
      case exception: Exception =>
        println(s"error downloading ${track.title}: ${exception}")
    } finally {
      fos.foreach(_.close())
      rbc.foreach(_.close())
      dlStream.foreach(_.close())
    }
  }

  private def parseResponse[T](response: ResponseType)(implicit decoder: Decoder[T]): Option[T] = {
    response.body match {
      case Left(error) =>
        println(error)
        None
      case Right(json) =>
        Some(JsonUtil.fromJson[T](json))
    }
  }
}

object SoundCloudConstants {
  val JsBundleRegex: Regex = "https://a-v2.sndcdn.com/assets/[0-9]+-.*.js".r
  val ClientIdRegex: Regex = """client_id:"([A-Za-z0-9]+)"""".r

  val FALLBACK_CLIENT_ID = "1CaOnsDIpR4VuCkgEPDmgAWP2g9lyG2S"
}