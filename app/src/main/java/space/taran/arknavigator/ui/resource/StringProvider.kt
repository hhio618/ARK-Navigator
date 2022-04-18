package space.taran.arknavigator.ui.resource

import android.content.Context
import androidx.annotation.StringRes
import space.taran.arknavigator.R
import space.taran.arknavigator.mvp.model.repo.kind.KindCode

class StringProvider(private val context: Context) {
    fun getString(@StringRes stringResId: Int): String {
        return context.getString(stringResId)
    }

    fun kindToString(kind: KindCode) = when (kind) {
        KindCode.Image -> context.getString(R.string.kind_image)
        KindCode.Video -> context.getString(R.string.kind_video)
        KindCode.Document -> context.getString(R.string.kind_document)
        KindCode.Link -> context.getString(R.string.kind_link)
    }
}
