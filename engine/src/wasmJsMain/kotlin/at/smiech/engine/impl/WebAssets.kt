package at.smiech.engine.impl

import kotlinx.browser.window
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.w3c.fetch.Response

/**
 * The game's asset files, fetched from beside the page, under [base].
 *
 * The game asks for its pictures and sounds as it builds a run, and has them at once on every other
 * platform, so a page fetches those ahead of time ([preload]) and hands them out from memory. Music
 * is fetched when it is first made instead ([fetch]), being most of the download.
 */
class WebAssets(private val base: String = "assets/") {
    private val preloaded = HashMap<String, ArrayBuffer>()

    /** Fetches all of [names] at once, for [buffer] and [bytes] to hand out. */
    suspend fun preload(names: Collection<String>) {
        val fetched = coroutineScope { names.map { async { it to fetch(it) } }.awaitAll() }
        preloaded.putAll(fetched)
    }

    /** The file [name], which [preload] has fetched. */
    fun buffer(name: String): ArrayBuffer =
        preloaded[name] ?: error("Asset $name was not preloaded")

    /** The file [name], which [preload] has fetched, as bytes. */
    fun bytes(name: String): ByteArray = Int8Array(buffer(name)).toByteArray()

    /** The file [name], fetched now. Fails if the server does not have it. */
    suspend fun fetch(name: String): ArrayBuffer = response(name).arrayBuffer().await()

    /** The text file [name], fetched now. */
    suspend fun text(name: String): String = response(name).text().await<JsString>().toString()

    private suspend fun response(name: String): Response {
        val response = window.fetch(base + name).await<Response>()
        check(response.ok) { "Asset $name could not be fetched: HTTP ${response.status}" }
        return response
    }
}
