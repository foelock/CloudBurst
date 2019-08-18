package com.github.foelock.cloudburst

class Wiring(baseUserDirectory: String) {
  val programLocalStorageService = new ProgramLocalStorageService(baseUserDirectory)
  val scApiClient = new SoundCloudApiClient(
    programLocalStorageService = programLocalStorageService,
    baseDownloadPath = s"$baseUserDirectory/soundcloud",
    clientIdOverride = None
  )
}
