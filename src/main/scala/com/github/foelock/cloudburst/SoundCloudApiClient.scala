package com.github.foelock.cloudburst

import java.io.{File, FileOutputStream, InputStream}
import java.net.URL
import java.nio.channels.{Channels, ReadableByteChannel}
import java.nio.file.{Files, Paths}

import com.github.foelock.cloudburst.SoundcloudConstants._
import com.github.foelock.cloudburst.domain.{Track, User}
import com.github.foelock.cloudburst.util.JsonUtil
import com.softwaremill.sttp._
import io.circe.Decoder

import scala.util.matching.Regex

class SoundCloudApiClient(
  programLocalStorageService: ProgramLocalStorageService,
  baseDownloadPath: String,
  clientIdOverride: Option[String] = None) {

  private implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

  private val clientId = clientIdOverride.orElse(instantiateClientId).getOrElse(FALLBACK_CLIENT_ID)

  private val downloadPath = {
    val path = Paths.get(baseDownloadPath)
    if (!Files.exists(path)) {
      Files.createDirectories(path)
    }
    path
  }

  private def fetchNewClientId(): Option[String] = {
    val request = sttp.get(uri"https://soundcloud.com/")
    val response = request.send()
    val jsBundleUrl = JsBundleRegex.findFirstMatchIn(response.unsafeBody).get
    val jsBundle = sttp.get(uri"$jsBundleUrl").send().unsafeBody
    val maybeClientId = ClientIdRegex.findFirstMatchIn(jsBundle).map(_.group(1))
    programLocalStorageService.store(programLocalStorageService.current.copy(clientId = maybeClientId))
    maybeClientId
  }

  private val baseUrl = "https://api.soundcloud.com/"

  private def apiUrlFor(resource: String, resourceId: Option[String] = None, queryParams: Map[String, String] = Map()): Uri = {
    val qps = queryParams.map { case (k, v) => s"$k=$v" }.mkString("&", "&", "")
    val url = s"$baseUrl$resource${resourceId.map(rid => s"/$rid").getOrElse("")}?client_id=$clientId$qps"
    uri"$url"
  }

  private def instantiateClientId: Option[String] = {
    val localClientId = programLocalStorageService.current.clientId
    val localClientIdWorks = localClientId.exists { cid =>
      val request = sttp.get(uri"https://api.soundcloud.com/tracks?client_id=$cid")
      val response = request.send()
      response.code == StatusCodes.Ok
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
    val request = sttp.get(apiUrlFor("tracks", Some(id.toString)))
    val response = request.send()
    parseResponse[Track](response)
  }

  def getTrackByUrl(url: String): Option[Track] = {
    val request = sttp.get(apiUrlFor(resource = "resolve", queryParams = Map("url" -> url)))
    val response: Id[Response[String]] = request.send()
    parseResponse[Track](response)
  }

  def getUserById(id: Long): Option[User] = {
    val request = sttp.get(uri"http://api.soundcloud.com/users/$id?client_id=$clientId")

    val response = request.send()

    println(response.unsafeBody)
    None
  }

  // todo: set metadata tags
  def downloadTrack(track: Track): Unit = {
    val dlUrl = new URL(s"${track.stream_url}?client_id=$clientId")

    var dlStream: Option[InputStream] = None
    var rbc: Option[ReadableByteChannel] = None
    var fos: Option[FileOutputStream] = None

    try {
      dlStream = Some(dlUrl.openStream())
      rbc = Some(Channels.newChannel(dlStream.get))
      val dlFile = Files.createFile(Paths.get(downloadPath.toString, s"${track.title}.mp3")).toFile
      fos = Some(new FileOutputStream(dlFile))
      fos.get.getChannel.transferFrom(rbc.get, 0, Long.MaxValue)
    } catch {
      case exception: Exception => println(exception.getMessage)
    } finally {
      fos.foreach(_.close())
      rbc.foreach(_.close())
      dlStream.foreach(_.close())
    }
  }

  private def parseResponse[T](response: Id[Response[String]])(implicit decoder: Decoder[T]): Option[T] = {
    response.body match {
      case Left(error) =>
        println(error)
        None
      case Right(json) =>
        Some(JsonUtil.fromJson[T](json))
    }
  }
}

object SoundcloudConstants {
  val JsBundleRegex: Regex = "https://a-v2.sndcdn.com/assets/app-.*\\.js".r
  val ClientIdRegex: Regex = """client_id:"([A-Za-z0-9]+)"""".r

  val FALLBACK_CLIENT_ID = "1CaOnsDIpR4VuCkgEPDmgAWP2g9lyG2S"
}