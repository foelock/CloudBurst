package com.mattbague.cloudburst

import com.mattbague.cloudburst.api.SoundCloudApiClient
import com.mattbague.cloudburst.util.ProgramLocalStorageService

class Wiring(userHomeDir: String, downloadDir: String) {
  val programLocalStorageService = new ProgramLocalStorageService(userHomeDir)
  val apiClient = new SoundCloudApiClient(
    programLocalStorageService = programLocalStorageService,
    baseDownloadPath = downloadDir,
    clientIdOverride = None
  )
}
