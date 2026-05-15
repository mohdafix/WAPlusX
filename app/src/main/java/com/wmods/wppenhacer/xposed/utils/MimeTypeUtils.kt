package com.wmods.wppenhacer.xposed.utils

import android.webkit.MimeTypeMap

object MimeTypeUtils {
    @JvmStatic
    fun getMimeTypeFromExtension(url: String?): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        return if (extension != null) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: ""
        } else {
            ""
        }
    }
}
