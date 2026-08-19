package io.ak1.testuilauncher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Where the downloaded Gemma model lives. */
fun gemmaModelFile(context: Context): File = File(context.filesDir, "llm/gemma.task")

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
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            dest.parentFile?.mkdirs()
            val part = File(dest.path + ".part")

            var url = urlString
            var redirects = 0
            var connection: HttpURLConnection
            while (true) {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
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
                    connection.disconnect()
                    throw IOException("download failed: HTTP $code")
                }
                break
            }

            val total = connection.contentLengthLong
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
}
