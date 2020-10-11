package com.mattbague.cloudburst

import com.typesafe.scalalogging.StrictLogging
import org.rogach.scallop._

object CLICloudBurstRunner extends StrictLogging {

  def main(args: Array[String]): Unit = {
    val cliConfig = new CliConfig(args)

    val userHomeDir = sys.props.get("user.home").getOrElse(".")

    val downloadDir = cliConfig.downloads.getOrElse(userHomeDir + "/soundcloud")

    val wiring = new Wiring(userHomeDir, downloadDir)
    val localStorage = wiring.programLocalStorageService
    val apiClient = wiring.apiClient

    def downloadSingleTrack(): Unit = {
      for {
        trackUrl <- cliConfig.track.toOption
        track <- apiClient.getTrackByUrl(trackUrl)
      } {
        apiClient.downloadTrack(track)
      }
    }

    def downloadLikedTracks(): Unit = {
      if (cliConfig.likes.toOption.getOrElse(false)) {
        val userId = if (cliConfig.user.isEmpty) {
          if (localStorage.current.userId.isEmpty) {
            throw new RuntimeException("Need a user ID to download all liked tracks")
          } else {
            val storedUserId = localStorage.current.userId.get
            logger.info(s"No user ID specified. Using previously stored user ID: $storedUserId")
            storedUserId
          }
        } else {
          val incomingUserId = cliConfig.user.toOption.get
          localStorage.store(localStorage.current.copy(userId = Some(incomingUserId)))
          incomingUserId
        }

        apiClient.getUserLikesById(userId)
        val userLikeTracks = apiClient.getUserLikesById(userId)

        val totalTracks = userLikeTracks.size

        userLikeTracks.headOption.zipWithIndex.foreach { case (track, ndx) =>
          logger.info(s"Processing track $ndx of $totalTracks")
          apiClient.downloadTrack(track)
        }
      }
    }

    try {
      downloadSingleTrack()
      downloadLikedTracks()
    } catch {
      case e: Exception => logger.error("An error occurred. Exiting.", e)
    } finally {
      localStorage.shutdown()
    }
  }
}

class CliConfig(arguments: Seq[String]) extends ScallopConf(arguments) {
  val track: ScallopOption[String] = opt[String](short = 't', descr = "Download single track via URL")
  val downloads: ScallopOption[String] = opt[String](short = 'd', descr = "Download folder location")
  val user: ScallopOption[Int] = opt[Int](
    short = 'u', descr =
      """
        |Soundcloud Account ID. To find this ID, go to your cookies and look at your 'oauth_token'.
        | Example: oauth_token='1-123456-7777777-ABCdefGHIjkLMn', then your ID would be '7777777'.
        | NOTE: This is not the ID you see in your profile URL."""
        .stripMargin.trim.split("\n").mkString
  )
  val likes: ScallopOption[Boolean] = opt[Boolean](
    short = 'l',
    descr = "Download all liked songs. Requires 'user' arg to be set at least once previously")
  verify()
}

