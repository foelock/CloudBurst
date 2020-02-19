package com.github.foelock.cloudburst

object CloudBurstRunner extends App {
  val userDirectory = sys.props.get("user.home").getOrElse(".")


  val clientIdOverride = sys.props.get("client_id")

  val wiring = new Wiring(userDirectory)

  val apiClient = wiring.scApiClient

//  val result = apiClient.getTrackById()
//  val result = apiClient.getTrackByUrl("https://soundcloud.com/plexitofer/cupid-groove")
  val result = apiClient.getTrackByUrl("https://soundcloud.com/topazeclub/city-night-shadows")

//  println(JsonUtil.toJson(result, true))

  apiClient.downloadTrack(result.get)
  wiring.programLocalStorageService.shutdown()

  System.exit(0)
}
