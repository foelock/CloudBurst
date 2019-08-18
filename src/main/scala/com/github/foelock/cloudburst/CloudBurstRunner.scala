package com.github.foelock.cloudburst

object CloudBurstRunner extends App {
  val userDirectory = sys.props.get("user.home").getOrElse(".")


  val clientIdOverride = sys.props.get("client_id")

  val wiring = new Wiring(userDirectory)

  val apiClient = wiring.apiClient

//  val result = apiClient.getTrackById()
//  val result = apiClient.getTrackByUrl("https://soundcloud.com/plexi-two/a-certian-intrumental")
  val result = apiClient.getUserById(269568808)
//  println(JsonUtil.toJson(result, true))

  wiring.programLocalStorageService.shutdown()

  System.exit(0)
}
