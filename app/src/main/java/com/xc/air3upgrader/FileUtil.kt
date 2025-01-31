package com.xc.air3upgrader

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object FileUtil {
    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "com.xc.air3upgrader.provider", file)
    }
}