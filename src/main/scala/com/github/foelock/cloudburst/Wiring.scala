package com.github.foelock.cloudburst

class Wiring(baseUserDirectory: String) {
  val programLocalStorageService = new ProgramLocalStorageService(baseUserDirectory)
  val apiClient = new ApiClient(programLocalStorageService, None)
}
