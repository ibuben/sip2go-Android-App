package uz.ex.sip2go.recording

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import uz.ex.sip2go.R
import java.io.File

object CallRecordingShare {
    fun share(context: Context, path: String, title: String) {
        val file = File(path)
        if (!file.exists()) return
        val appContext = context.applicationContext
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, appContext.getString(R.string.history_share_recording)),
        )
    }
}
