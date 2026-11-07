package com.foyerlauncher.foyer.ai.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Where the active model lives — downloaded or copied in from local storage. */
fun modelFile(context: Context): File = File(context.filesDir, "llm/model.task")

/**
 * Streams a model file to local storage with progress. Follows redirects
 * manually so a Hugging Face `resolve` URL (which 302s to a signed CDN link)
 * works, and only sends the auth token to the first host — never to the CDN.
 */
object ModelDownloader {
    suspend fun download(
        urlString: String,
        dest: File,
        token: String?,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                Log.d(
                    AI_TAG,
                    "download start url=$urlString token=${token?.take(6)}…(len=${token?.length ?: 0})",
                )
                dest.parentFile?.mkdirs()
                val part = File(dest.path + ".part")

                var url = urlString
                var redirects = 0
                var connection: HttpURLConnection
                while (true) {
                    connection =
                        (URL(url).openConnection() as HttpURLConnection).apply {
                            instanceFollowRedirects = false
                            connectTimeout = 30_000
                            readTimeout = 30_000
                            if (redirects == 0 && !token.isNullOrBlank()) {
                                setRequestProperty("Authorization", "Bearer $token")
                            }
                        }
                    val code = connection.responseCode
                    if (code in 300..399) {
                        val location = connection.getHeaderField("Location")
                        connection.disconnect()
                        if (location.isNullOrBlank() || ++redirects > 6) {
                            throw IOException("too many redirects")
                        }
                        url = location
                        continue
                    }
                    if (code != HttpURLConnection.HTTP_OK) {
                        val body =
                            runCatching {
                                connection.errorStream?.bufferedReader()?.readText()
                            }.getOrNull()?.take(300)
                        val hint = connection.getHeaderField("X-Error-Message")
                        connection.disconnect()
                        Log.w(AI_TAG, "download HTTP $code · hint=$hint · body=$body")
                        throw IOException("HTTP $code${hint?.let { " · $it" } ?: ""}${body?.let { " · $it" } ?: ""}")
                    }
                    break
                }

                val total = connection.contentLengthLong
                // Abort before streaming if the file can't fit: need the model
                // itself plus ~150 MB of headroom so we never wedge the device.
                if (total > 0) {
                    val free = android.os.StatFs(dest.parentFile!!.path).availableBytes
                    if (free < total + 150L * 1024 * 1024) {
                        connection.disconnect()
                        throw IOException(
                            "not enough storage — model is ${total / 1_000_000} mb, only ${free / 1_000_000} mb free",
                        )
                    }
                }
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(1 shl 16)
                        var downloaded = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            downloaded += n
                            onProgress(downloaded, total)
                        }
                    }
                }
                connection.disconnect()

                if (dest.exists()) dest.delete()
                if (!part.renameTo(dest)) throw IOException("could not finalize model file")
                Log.d(AI_TAG, "model downloaded: ${dest.path} (${dest.length()} bytes)")
                Unit
            }.onFailure { Log.w(AI_TAG, "model download failed: ${it.message}", it) }
        }

    /** Copies a model file the user already has on-device (picked via SAF) into place. */
    suspend fun installLocal(
        context: Context,
        uri: Uri,
        dest: File,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                dest.parentFile?.mkdirs()
                val part = File(dest.path + ".part")
                val input =
                    context.contentResolver.openInputStream(uri)
                        ?: throw IOException("couldn't open that file")
                input.use { inStream -> part.outputStream().use { out -> inStream.copyTo(out) } }
                if (dest.exists()) dest.delete()
                if (!part.renameTo(dest)) throw IOException("could not finalize model file")
                Log.d(AI_TAG, "model installed from local file: ${dest.path} (${dest.length()} bytes)")
                Unit
            }.onFailure { Log.w(AI_TAG, "local model install failed: ${it.message}", it) }
        }
}
