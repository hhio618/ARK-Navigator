package space.taran.arknavigator.ui.fragments.dialog

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import space.taran.arknavigator.databinding.DialogInfoBinding
import space.taran.arknavigator.utils.extensions.textOrGone

class ConfirmationDialogFragment : DialogFragment() {

    private lateinit var binding: DialogInfoBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)

        binding = DialogInfoBinding.inflate(inflater, container, false)

        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val args = requireArguments()

        binding.apply {
            titleTV.text = args.getString(TITLE)
            infoTV.text = args.getString(DESCRIPTION)
            posBtn.text = args.getString(POSITIVE_BTN_KEY)
            negBtn.textOrGone(args.getString(NEGATIVE_BTN_KEY))

            posBtn.setOnClickListener {
                val requestKey = args
                    .getString(POSITIVE_REQUEST_KEY) ?: DEFAULT_POSITIVE_REQUEST_KEY
                setFragmentResult(
                    requestKey,
                    args.getBundle(EXTRA_BUNDLE) ?: bundleOf()
                )
                dismiss()
            }

            negBtn.setOnClickListener {
                dismiss()
            }
        }

        return binding.root
    }

    companion object {
        private const val TITLE = "title"
        private const val DESCRIPTION = "description"
        private const val POSITIVE_BTN_KEY = "positive_key"
        private const val NEGATIVE_BTN_KEY = "negative_key"
        private const val POSITIVE_REQUEST_KEY = "positiveRequestKey"
        const val DEFAULT_POSITIVE_REQUEST_KEY = "defaultPositiveRequestKey"
        const val CONFIRMATION_DIALOG_TAG = "confirmationDialogFragment"
        const val EXTRA_BUNDLE = "extraBundle"

        fun newInstance(
            title: String,
            description: String,
            posBtnText: String,
            negBtnText: String?,
            positiveRequestKey: String? = null,
            bundle: Bundle? = null
        ): ConfirmationDialogFragment = ConfirmationDialogFragment().also { f ->
            f.arguments = bundleOf(
                TITLE to title,
                DESCRIPTION to description,
                POSITIVE_BTN_KEY to posBtnText,
                NEGATIVE_BTN_KEY to negBtnText,
                POSITIVE_REQUEST_KEY to positiveRequestKey,
                EXTRA_BUNDLE to bundle
            )
        }
    }
}
