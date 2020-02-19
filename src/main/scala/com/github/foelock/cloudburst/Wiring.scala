package com.github.foelock.cloudburst

import com.github.foelock.cloudburst.api.SoundCloudApiClient
import com.github.foelock.cloudburst.util.ProgramLocalStorageService

class Wiring(baseUserDirectory: String) {
  val programLocalStorageService = new ProgramLocalStorageService(baseUserDirectory)
  val scApiClient = new SoundCloudApiClient(
    programLocalStorageService = programLocalStorageService,
    baseDownloadPath = s"$baseUserDirectory/soundcloud",
    clientIdOverride = None
  )
}
