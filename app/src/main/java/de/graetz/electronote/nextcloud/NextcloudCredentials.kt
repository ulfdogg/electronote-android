package de.graetz.electronote.nextcloud

data class NextcloudCredentials(
    val serverUrl: String,
    val loginName: String,
    val appPassword: String
)
