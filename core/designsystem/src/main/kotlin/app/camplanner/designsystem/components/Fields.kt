package app.camplanner.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.camplanner.designsystem.theme.Atlas

/**
 * A field written on a ruled line, like an entry in a ledger: no box, just a hairline under the
 * value that turns wine while focused.
 */
@Composable
fun LedgerField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    suffix: String? = null,
    label: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    textStyle: TextStyle = Atlas.type.body,
    textAlign: TextAlign = TextAlign.Start,
    singleLine: Boolean = true,
    minWidth: Dp = 0.dp,
) {
    val c = Atlas.colors
    var focused by remember { mutableStateOf(false) }
    Column(modifier) {
        if (label != null) {
            Text(label.uppercase(), style = Atlas.type.overline, color = c.textSecondary)
            Spacer(Modifier.height(Atlas.space.xs))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f, fill = false).widthIn(min = minWidth)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    textStyle = textStyle.copy(color = c.text, textAlign = textAlign),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    modifier = Modifier
                        .widthIn(min = minWidth)
                        .onFocusChanged { focused = it.isFocused }
                        .padding(vertical = Atlas.space.xs),
                    decorationBox = { inner ->
                        Box {
                            if (value.isEmpty()) {
                                Text(placeholder, style = textStyle.copy(textAlign = textAlign), color = c.textSecondary.copy(alpha = 0.6f))
                            }
                            inner()
                        }
                    },
                )
            }
            if (suffix != null) {
                Spacer(Modifier.width(Atlas.space.xs))
                Text(suffix, style = Atlas.type.bodySmall, color = c.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
        Hairline(Modifier.widthIn(min = minWidth), color = if (focused) c.accent else c.hairline)
    }
}

/** Small number entry for pages, reps and seconds. */
@Composable
fun NumberEntry(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "0",
    suffix: String? = null,
) {
    LedgerField(
        value = value,
        onValueChange = { new -> if (new.length <= 4 && new.all(Char::isDigit)) onValueChange(new) },
        modifier = modifier,
        placeholder = placeholder,
        suffix = suffix,
        keyboardType = KeyboardType.Number,
        textStyle = Atlas.type.numeral.copy(fontSize = Atlas.type.title.fontSize),
        textAlign = TextAlign.End,
        minWidth = 44.dp,
    )
}
