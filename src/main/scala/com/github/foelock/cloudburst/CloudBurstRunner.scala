package com.github.foelock.cloudburst

import com.github.foelock.cloudburst.api.SoundCloudConstants
import scalaj.http.Http

object CloudBurstRunner extends App {
  val userDirectory = sys.props.get("user.home").getOrElse(".")


  val clientIdOverride = sys.props.get("client_id")

  val wiring = new Wiring(userDirectory)

  val apiClient = wiring.scApiClient

//  val result = apiClient.getTrackById()
//  val result = apiClient.getTrackByUrl("https://soundcloud.com/plexitofer/cupid-groove")
//  val result = apiClient.getTrackByUrl("https://soundcloud.com/topazeclub/city-night-shadows")
//  apiClient.downloadTrack(result.get)

  val userId = 269568808

  val userLikeTracks = apiClient.getUserLikesById(userId)

  userLikeTracks.foreach { track =>
    apiClient.downloadTrack(track)
    Thread.sleep(SoundCloudConstants.API_DOWNLOAD_DELAY_MS)
  }
//  println(JsonUtil.toJson(result, true))

  wiring.programLocalStorageService.shutdown()

  System.exit(0)
}
