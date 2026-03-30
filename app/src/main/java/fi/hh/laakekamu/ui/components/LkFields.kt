package fi.hh.laakekamu.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fi.hh.laakekamu.R
import fi.hh.laakekamu.ui.format.ProvideFinnishLocale
import fi.hh.laakekamu.ui.format.formatDate
import fi.hh.laakekamu.ui.format.formatTime
import fi.hh.laakekamu.ui.theme.LaakeKamuTheme
import fi.hh.laakekamu.ui.theme.LkDanger
import fi.hh.laakekamu.ui.theme.LkInput
import fi.hh.laakekamu.ui.theme.LkPlaceholder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Figma: fields 310 x 60, square, label about 36dp above the field's top edge.
private val FIELD_WIDTH = 310.dp
private val FIELD_HEIGHT = 60.dp
private val LABEL_GAP = 8.dp
private val TEXT_INSET = 13.dp
private val DROPDOWN_INSET = 9.dp
private val CARET_SIZE = 30.dp
private val CARET_END_PADDING = 12.dp
private val ERROR_BORDER = 2.dp

/** Label, field and an optional error text under it, as in the wizard and symptom forms. */
@Composable
private fun LabeledField(
    label: String?,
    error: String?,
    modifier: Modifier,
    field: @Composable () -> Unit
) {
    Column(modifier = modifier.widthIn(max = FIELD_WIDTH).fillMaxWidth()) {
        if (label != null) {
            // The field itself carries the label for TalkBack, so it's read once.
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clearAndSetSemantics {}
            )
            Spacer(Modifier.size(LABEL_GAP))
        }
        field()
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = LkDanger,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** The grey input box: 60dp high (taller at big font), with a danger border when invalid. */
private fun Modifier.fieldBox(hasError: Boolean): Modifier = this
    .fillMaxWidth()
    .heightIn(min = FIELD_HEIGHT)
    .background(LkInput)
    .then(if (hasError) Modifier.border(ERROR_BORDER, LkDanger) else Modifier)

/** A field's value, or its 70 % black placeholder when empty. */
@Composable
private fun ValueOrPlaceholder(value: String?, placeholder: String) {
    Text(
        text = value ?: placeholder,
        style = MaterialTheme.typography.bodyLarge,
        color = if (value == null) LkPlaceholder else Color.Black
    )
}

/** A single-line text input. */
@Composable
fun LkTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    LabeledField(label, error, modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
            keyboardOptions = keyboardOptions,
            modifier = Modifier
                .fieldBox(error != null)
                .semantics {
                    contentDescription = label
                    if (error != null) error(error)
                },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.padding(horizontal = TEXT_INSET),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) ValueOrPlaceholder(null, placeholder)
                    innerTextField()
                }
            }
        )
    }
}

/**
 * A clickable read-only field: the base of the dropdown, date and time fields. [showCaret] adds
 * the Figma caret on the right (dropdowns).
 */
@Composable
private fun ClickableField(
    value: String?,
    placeholder: String,
    description: String,
    hasError: Boolean,
    showCaret: Boolean,
    role: Role,
    enabled: Boolean,
    onClick: () -> Unit,
    error: String? = null,
    expanded: Boolean? = null,
    onCollapse: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fieldBox(hasError)
            .clickable(enabled = enabled, role = role, onClick = onClick)
            .semantics {
                contentDescription = "$description: ${value ?: placeholder}"
                if (error != null) error(error)
                // Dropdowns tell TalkBack whether their menu is open.
                when (expanded) {
                    true -> collapse {
                        onCollapse()
                        true
                    }
                    false -> expand {
                        onClick()
                        true
                    }
                    null -> Unit
                }
            }
            .padding(start = DROPDOWN_INSET, end = CARET_END_PADDING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) { ValueOrPlaceholder(value, placeholder) }
        if (showCaret) {
            Image(
                painter = painterResource(R.drawable.ic_caret_down),
                contentDescription = null,
                modifier = Modifier.size(CARET_SIZE)
            )
        }
    }
}

/** A dropdown field with the Figma caret; [options] open in a menu under it. */
@Composable
fun <T> LkDropdown(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    LabeledField(label, error, modifier) {
        Box {
            ClickableField(
                value = selected?.let(optionLabel),
                placeholder = placeholder,
                description = label,
                hasError = error != null,
                showCaret = true,
                role = Role.DropdownList,
                enabled = enabled,
                onClick = { expanded = true },
                error = error,
                expanded = expanded,
                onCollapse = { expanded = false }
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(optionLabel(option), style = MaterialTheme.typography.bodyLarge)
                        },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        }
                    )
                }
            }
        }
    }
}

/**
 * A date field that opens the M3 date picker in Finnish (Monday first). [clearable] adds a
 * "Tyhjennä" button for optional dates such as Lopetuspäivä.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LkDateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    clearable: Boolean = false
) {
    var open by rememberSaveable { mutableStateOf(false) }
    LabeledField(label, error, modifier) {
        ClickableField(
            value = date?.let(::formatDate),
            placeholder = stringResource(R.string.paivamaara_placeholder),
            description = label,
            hasError = error != null,
            showCaret = false,
            role = Role.Button,
            enabled = true,
            onClick = { open = true },
            error = error
        )
    }
    if (open) {
        ProvideFinnishLocale {
            val state = rememberDatePickerState(initialSelectedDateMillis = date?.toUtcMillis())
            DatePickerDialog(
                onDismissRequest = { open = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { onDateChange(it.toUtcLocalDate()) }
                        open = false
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    Row {
                        if (clearable) {
                            TextButton(onClick = {
                                onDateChange(null)
                                open = false
                            }) { Text(stringResource(R.string.tyhjenna)) }
                        }
                        TextButton(onClick = { open = false }) {
                            Text(stringResource(R.string.peruuta))
                        }
                    }
                }
            ) {
                DatePicker(
                    state = state,
                    title = {
                        Text(
                            text = stringResource(R.string.valitse_paiva),
                            modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                        )
                    }
                )
            }
        }
    }
}

/**
 * A reminder time field ("klo 8.00") that opens the M3 time picker dialog in 24-hour **text
 * input** mode, switchable to the dial. Times are minutes after midnight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LkTimeField(
    minuteOfDay: Int?,
    onTimeChange: (Int) -> Unit,
    description: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    error: String? = null
) {
    var open by rememberSaveable { mutableStateOf(false) }
    LabeledField(label = null, error = error, modifier = modifier) {
        ClickableField(
            value = minuteOfDay?.let { stringResource(R.string.klo_aika, formatTime(it)) },
            placeholder = placeholder,
            description = description,
            hasError = error != null,
            showCaret = true,
            role = Role.Button,
            enabled = true,
            onClick = { open = true },
            error = error
        )
    }
    if (open) {
        ProvideFinnishLocale {
            val state = rememberTimePickerState(
                initialHour = (minuteOfDay ?: 0) / 60,
                initialMinute = (minuteOfDay ?: 0) % 60,
                is24Hour = true
            )
            var mode by remember { mutableStateOf(TimePickerDisplayMode.Input) }
            TimePickerDialog(
                onDismissRequest = { open = false },
                confirmButton = {
                    TextButton(onClick = {
                        onTimeChange(state.hour * 60 + state.minute)
                        open = false
                    }) { Text(stringResource(R.string.ok)) }
                },
                title = { Text(stringResource(R.string.valitse_aika)) },
                modeToggleButton = {
                    TimePickerDialogDefaults.DisplayModeToggle(
                        onDisplayModeChange = {
                            mode = if (mode == TimePickerDisplayMode.Input) {
                                TimePickerDisplayMode.Picker
                            } else {
                                TimePickerDisplayMode.Input
                            }
                        },
                        displayMode = mode
                    )
                },
                dismissButton = {
                    TextButton(onClick = {
                        open = false
                    }) { Text(stringResource(R.string.peruuta)) }
                }
            ) {
                if (mode == TimePickerDisplayMode.Input) TimeInput(state) else TimePicker(state)
            }
        }
    }
}

/** The M3 date picker works in UTC milliseconds at midnight. */
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Preview(widthDp = 412, heightDp = 917, showBackground = true)
@Composable
private fun LkFieldsPreview() {
    LaakeKamuTheme {
        Column(
            modifier = Modifier.padding(start = 51.dp, top = 78.dp),
            verticalArrangement = Arrangement.spacedBy(41.dp)
        ) {
            LkTextField("Lääke", "", {}, "Kirjoita lääkkeen nimi")
            LkDropdown("Tyyppi", listOf("tabletti"), null, { it }, {}, "Valitse lääkkeen tyyppi")
            LkTextField("Annosmäärä", "5", {}, "Kirjoita", error = "Annosmäärä puuttuu")
            LkDateField("Lääkkeen lopetuspäivä", null, {}, clearable = true)
            LkTimeField(480, {}, "Aika", "Valitse aika", Modifier.width(310.dp))
        }
    }
}
