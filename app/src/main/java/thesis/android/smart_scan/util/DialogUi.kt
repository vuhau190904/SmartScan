package thesis.android.smart_scan.util

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import thesis.android.smart_scan.R

/**
 * Ô nhập trong dialog: Filled, bo góc, không kẻ gạch chân kiểu EditText cũ.
 */
fun AppCompatActivity.inflateDialogTextInput(hint: String, initialText: String? = null): Pair<View, TextInputEditText> {
    val root = LayoutInflater.from(this).inflate(R.layout.dialog_text_input, null, false)
    val til = root.findViewById<TextInputLayout>(R.id.dialogTextInputLayout)
    val edt = root.findViewById<TextInputEditText>(R.id.dialogEditText)
    til.hint = hint
    if (!initialText.isNullOrEmpty()) edt.setText(initialText)
    return root to edt
}
