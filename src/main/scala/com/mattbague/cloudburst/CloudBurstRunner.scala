package com.mattbague.cloudburst

import com.mattbague.cloudburst.api.SoundCloudConstants
import com.typesafe.scalalogging.LazyLogging

object CloudBurstRunner extends App with LazyLogging {

  val userDirectory = sys.props.get("user.home").getOrElse(".")


  val clientIdOverride = sys.props.get("client_id")

  val wiring = new Wiring(userDirectory)

  val apiClient = wiring.scApiClient

  //  val result = apiClient.getTrackById()
  //  val result = apiClient.getTrackByUrl("https://soundcloud.com/plexitofer/cupid-groove")
//    val result = apiClient.getTrackByUrl("https://soundcloud.com/skibblez/sky-romance")
//    apiClient.downloadTrack(result.get)

  val userId = 269568808

  val userLikeTracks = apiClient.getUserLikesById(userId)

  val totalTracks = userLikeTracks.size

  userLikeTracks.zipWithIndex.foreach { case (track, ndx) =>
    logger.info(s"Processing track $ndx of $totalTracks")
    apiClient.downloadTrack(track)
  }
  //  println(JsonUtil.toJson(result, true))

  wiring.programLocalStorageService.shutdown()

  System.exit(0)
}
