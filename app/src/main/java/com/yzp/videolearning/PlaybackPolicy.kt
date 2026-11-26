package com.yzp.videolearning

import java.net.URI
import java.util.Locale

/** Applied to main-frame navigation; the provider may load HTTPS CDN subresources. */
object PlaybackPolicy {
    private val hosts = setOf("e54144713.at.baijiayun.com", "e54144713.at.baijiacloud.com")

    fun allows(url: String?): Boolean = runCatching {
        if (url.isNullOrBlank()) return false
        val uri = URI(url.trim())
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host?.lowercase(Locale.ROOT) in hosts &&
            uri.rawUserInfo == null &&
            (uri.port == -1 || uri.port == 443)
    }.getOrDefault(false)

    /** A JSON null/invalid result means the page provides no usable position. */
    fun positionFromJavascript(result: String?): Long? =
        result?.toDoubleOrNull()?.let(::positionMillis)

    fun positionMillis(seconds: Double): Long? =
        if (seconds.isFinite() && seconds >= 0.0 && seconds <= Long.MAX_VALUE / 1000.0) {
            (seconds * 1000).toLong()
        } else null
}
