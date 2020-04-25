package com.mattbague.cloudburst

import com.mattbague.cloudburst.api.SoundCloudApiClient
import com.mattbague.cloudburst.util.ProgramLocalStorageService

class Wiring(baseUserDirectory: String) {
  val programLocalStorageService = new ProgramLocalStorageService(baseUserDirectory)
  val scApiClient = new SoundCloudApiClient(
    programLocalStorageService = programLocalStorageService,
    baseDownloadPath = s"$baseUserDirectory/soundcloud",
    clientIdOverride = None
  )
}
