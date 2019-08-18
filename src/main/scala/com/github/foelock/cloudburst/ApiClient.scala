package com.github.foelock.cloudburst

import com.github.foelock.cloudburst.SoundcloudConstants._
import com.github.foelock.cloudburst.domain.{Track, User}
import com.github.foelock.cloudburst.util.JsonUtil
import com.softwaremill.sttp._

class ApiClient(programLocalStorageService: ProgramLocalStorageService, clientIdOverride: Option[String] = None) {

  private implicit val backend = HttpURLConnectionBackend()

  private val clientId = clientIdOverride.orElse(instantiateClientId).getOrElse(FALLBACK_CLIENT_ID)

  private def fetchNewClientId(): Option[String] = {
    val request = sttp.get(uri"https://soundcloud.com/")
    val response = request.send()
    val jsBundleUrl = JsBundleRegex.findFirstMatchIn(response.unsafeBody).get
    val jsBundle = sttp.get(uri"$jsBundleUrl").send().unsafeBody
    val maybeClientId = ClientIdRegex.findFirstMatchIn(jsBundle).map(_.group(1))
    programLocalStorageService.store(programLocalStorageService.current.copy(clientId = maybeClientId))
    maybeClientId
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
    val request = sttp.get(uri"https://api.soundcloud.com/tracks/${id}?client_id=${clientId}")
    val response = request.send()
    response.body match {
      case Left(error) =>
        println(s"Failed to get track ${id}. Reason: $error")
        None
      case Right(json) =>
        Some(JsonUtil.fromJson[Track](json))
    }
  }

  def getTrackByUrl(url: String): Option[Track] = {
    //    val request = sttp.get(uri"https://api.soundcloud.com/tracks/${id}?client_id=${clientId}")

    None
  }

  def getUserById(id: Long): Option[User] = {
    val request = sttp.get(uri"http://api.soundcloud.com/users/${id}?client_id=${clientId}")

    val response = request.send()

    println(response.unsafeBody)
    None
  }
}

object SoundcloudConstants {
  val JsBundleRegex = "https://a-v2.sndcdn.com/assets/app-.*\\.js".r
  val ClientIdRegex = """client_id:"([A-Za-z0-9]+)"""".r

  val FALLBACK_CLIENT_ID = "1CaOnsDIpR4VuCkgEPDmgAWP2g9lyG2S"
}