package com.secondpasslibrary.reader.design.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.secondpasslibrary.reader.design.icons.AppIcon
import com.secondpasslibrary.reader.design.icons.AppIconGraphic

@Composable
internal fun InlineSearchField(
    query: String,
    placeholder: String,
    contentDescription: String,
    onQueryChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        keyboard?.hide()
        onSubmit()
    }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier =
            modifier
                .fillMaxWidth()
                .semantics {
                    this.contentDescription = contentDescription
                },
        enabled = enabled,
        placeholder = { Text(placeholder) },
        leadingIcon = { AppIconGraphic(AppIcon.Search, null) },
        trailingIcon = {
            IconButton(onClick = submit, enabled = enabled) {
                AppIconGraphic(AppIcon.Search, "Submit search")
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submit() }),
        shape = RoundedCornerShape(18.dp)
    )
}
