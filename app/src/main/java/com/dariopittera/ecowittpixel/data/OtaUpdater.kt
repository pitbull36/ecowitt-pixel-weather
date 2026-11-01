package com.dariopittera.ecowittpixel.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tagName: String,
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkSize: Long
)

class OtaUpdater(private val context: Context) {

    suspend fun checkForUpdate(
        githubRepo: String,
        preferWatchAsset: Boolean = false
    ): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        try {
            val apiUrl = "https://api.github.com/repos/$githubRepo/releases/latest"
            val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "EcowittAtmosphere-Android")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            val responseCode = connection.responseCode
            if (responseCode == 404) {
                return@withContext Result.success(null)
            }
            if (responseCode !in 200..299) {
                val errorText = connection.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                return@withContext Result.failure(Exception("GitHub API returned HTTP $responseCode: $errorText"))
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)

            val tagName = json.optString("tag_name", "")
            val body = json.optString("body", "")
            val assets = json.optJSONArray("assets")

            var apkUrl: String? = null
            var apkSize: Long = 0L

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        val url = asset.optString("browser_download_url", "")
                        val size = asset.optLong("size", 0L)
                        val isWatchApk = name.contains("watch", ignoreCase = true) ||
                            name.contains("wear", ignoreCase = true)
                        if (preferWatchAsset && isWatchApk) {
                            apkUrl = url
                            apkSize = size
                            break
                        } else if (!preferWatchAsset && !isWatchApk) {
                            apkUrl = url
                            apkSize = size
                            break
                        }
                    }
                }
            }

            if (apkUrl.isNullOrBlank()) {
                val targetLabel = if (preferWatchAsset) "watch" else "phone"
                return@withContext Result.failure(
                    Exception("Latest release ($tagName) has no compatible $targetLabel .apk asset")
                )
            }

            val parsedVersionCode = parseVersionCode(tagName, body)
            val cleanVersionName = tagName.removePrefix("v").ifBlank { tagName }

            Result.success(
                ReleaseInfo(
                    tagName = tagName,
                    versionCode = parsedVersionCode,
                    versionName = cleanVersionName,
                    releaseNotes = body,
                    apkDownloadUrl = apkUrl,
                    apkSize = apkSize
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseVersionCode(tagName: String, body: String): Int {
        val bodyRegex = Regex("""versionCode\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
        bodyRegex.find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }

        val cleanTag = tagName.trim().removePrefix("v").removePrefix("V")
        cleanTag.toIntOrNull()?.let { return it }

        val parts = cleanTag.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.isNotEmpty()) {
            if (parts.size >= 3 && parts[0] == 1 && parts[1] == 0 && parts[2] >= 1) {
                return parts[2]
            }
            return parts[0]
        }
        return 1
    }

    suspend fun downloadApk(
        apkUrl: String,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val otaDir = File(context.cacheDir, "ota").apply {
                if (!exists()) mkdirs()
            }
            val outputFile = File(otaDir, "update.apk")
            if (outputFile.exists()) {
                outputFile.delete()
            }

            var currentUrl = apkUrl
            var redirects = 0
            var connection: HttpURLConnection

            while (true) {
                connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/octet-stream, application/vnd.android.package-archive, */*")
                    setRequestProperty("User-Agent", "EcowittAtmosphere-Android")
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                val status = connection.responseCode
                if (status in listOf(
                        HttpURLConnection.HTTP_MOVED_TEMP,
                        HttpURLConnection.HTTP_MOVED_PERM,
                        HttpURLConnection.HTTP_SEE_OTHER,
                        307,
                        308
                    )
                ) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (location.isNullOrBlank() || ++redirects > 8) {
                        return@withContext Result.failure(Exception("Too many redirects downloading APK"))
                    }
                    currentUrl = URL(URL(currentUrl), location).toString()
                } else {
                    break
                }
            }

            if (connection.responseCode !in 200..299) {
                return@withContext Result.failure(
                    Exception("Failed to download APK: HTTP ${connection.responseCode}")
                )
            }

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            onProgress((downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                    output.fd.sync()
                }
            }
            connection.disconnect()

            if (outputFile.length() < 100_000L) {
                return@withContext Result.failure(
                    Exception("Downloaded APK is incomplete (${outputFile.length()} bytes)")
                )
            }
            val magic = ByteArray(4)
            outputFile.inputStream().use { it.read(magic) }
            val isZipArchive = magic[0] == 0x50.toByte() &&
                magic[1] == 0x4B.toByte() &&
                magic[2] == 0x03.toByte() &&
                magic[3] == 0x04.toByte()
            if (!isZipArchive) {
                return@withContext Result.failure(
                    Exception("Downloaded file is not a valid APK archive")
                )
            }
            outputFile.setReadable(true, false)

            onProgress(1f)
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun canRequestPackageInstalls(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun openUnknownAppsSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun promptInstall(apkFile: File) {
        apkFile.setReadable(true, false)
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(installIntent)
    }
}
