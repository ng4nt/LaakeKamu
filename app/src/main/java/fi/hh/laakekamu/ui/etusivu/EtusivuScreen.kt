package fi.hh.laakekamu.ui.etusivu

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fi.hh.laakekamu.R
import fi.hh.laakekamu.domain.AnnoksenTila
import fi.hh.laakekamu.ui.components.LkPrimaryButton
import fi.hh.laakekamu.ui.format.formatDayMonth
import fi.hh.laakekamu.ui.format.formatTime
import fi.hh.laakekamu.ui.format.formatWeekday
import fi.hh.laakekamu.ui.format.formatWeekdayShort
import fi.hh.laakekamu.ui.format.formatWeekdayTitle
import fi.hh.laakekamu.ui.theme.LkDanger
import fi.hh.laakekamu.ui.theme.LkNavBar
import fi.hh.laakekamu.ui.theme.LkPrimary
import java.time.LocalDate
import java.time.LocalTime

// Figma: group heading 18sp with a 24dp sun/moon at x 34, rows with a 45dp checkbox at
// x 31 and the text 18dp after it, 28dp between rows (the evening group), about 40dp between
// groups. A row is a touch target of at least 48dp, so the gaps around it are 1.5dp smaller
// on each side to keep the checkboxes where Figma has them.
private val START = 31.dp
private val TIME_ICON = 24.dp
private val TIME_ICON_GAP = 11.dp
private val HEADING_TO_ROW = 10.5.dp
private val ROW_GAP = 25.dp
private val GROUP_GAP = 38.5.dp
private val ROW_MIN_HEIGHT = 48.dp
private val CHECKBOX = 45.dp
private val CHECKBOX_TEXT_GAP = 18.dp
private val MISSED_BORDER = 5.dp

// Figma: the fi-rs-check icon sits 5.63dp from the frame's top left corner.
private val CHECK_INSET = 5.63.dp

// Figma (y minus the 24dp status bar Figma doesn't have): the week strip 45dp from the
// top, 7 cells of 50dp at x 1 + 60 n with radius 20 when selected; the header 19dp under it;
// the first time group 88dp under the header. Figma fills that gap with the progress:
// its dots 25dp under the header and the time group 14dp under the % (20 + 7 + 22 between).
private val TOP = 45.dp
private val STRIP_EDGE = 1.dp
private val DAY_CELL = 50.dp
private val DAY_RADIUS = 20.dp
private val STRIP_TO_HEADER = 19.dp
private val HEADER_TO_LIST = 88.dp
private val HEADER_TO_PROGRESS = 25.dp
private val PROGRESS_TO_LIST = 14.dp
private val SIDE = 16.dp

/**
 * Etusivu: this week's strip, the live header, the selected day's progress and its doses
 * grouped by time with sun/moon headings and their state. Tapping a row ticks or unticks it
 *, except on future days, and a tick that can't be saved is reported in
 * [snackbarHostState]. The permission banners come later. [onAdd] opens the wizard from
 * the empty state.
 */
@Composable
fun EtusivuScreen(
    onAdd: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: EtusivuViewModel = viewModel(factory = EtusivuViewModel.Factory)
) {
    val latest by viewModel.uiState.collectAsStateWithLifecycle()
    // The day the screen came to the foreground on. Coming back (also from a locked screen,
    // which keeps the composition) refreshes it, so a list kept from before midnight shows as
    // loading until today's arrives, and restarts the clock.
    var shownDay by remember { mutableStateOf(viewModel.today()) }
    LifecycleStartEffect(Unit) {
        shownDay = viewModel.today()
        viewModel.refresh()
        onStopOrDispose {}
    }
    val state = shownState(latest, shownDay)

    // Shown once per failure; kept for the recreated screen if the activity is only rotating
    //.
    val virhe = stringResource(R.string.virhe_merkinta)
    val activity = LocalActivity.current
    val errorId = state.toggleErrorId
    LaunchedEffect(errorId) {
        if (errorId != 0L) {
            try {
                snackbarHostState.showSnackbar(virhe, duration = SnackbarDuration.Long)
            } finally {
                if (activity?.isChangingConfigurations != true) viewModel.toggleErrorShown(errorId)
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = TOP, bottom = 24.dp)
    ) {
        val paiva = state.paiva
        val tanaan = state.tanaan
        val nyt = state.nyt
        when {
            state.loadError -> Message(stringResource(R.string.virhe_etusivu))
            paiva == null || tanaan == null || nyt == null -> Unit // Loading.
            else -> {
                Viikko(state.viikko, paiva, tanaan, onSelect = { viewModel.selectDay(it, tanaan) })
                Spacer(Modifier.height(STRIP_TO_HEADER))
                Otsikko(paiva, tanaan, nyt)
                if (state.isEmpty) {
                    // No doses, no progress.
                    Spacer(Modifier.height(HEADER_TO_LIST))
                    EmptyState(onAdd)
                } else {
                    Spacer(Modifier.height(HEADER_TO_PROGRESS))
                    Edistys(state.tilat)
                    Spacer(Modifier.height(PROGRESS_TO_LIST))
                    Annokset(state.ryhmat, state.muokattavissa, onToggle = viewModel::toggle)
                }
            }
        }
    }
}

/**
 * This week, Monday to Sunday ("to" over "18"), with [paiva] selected. A cell is a 50dp touch
 * target, and TalkBack reads it as "torstai 18.12., tänään" with its selected state. Under 352dp
 * of width the cells share it equally instead of squeezing Sunday.
 */
@Composable
private fun Viikko(
    viikko: List<LocalDate>,
    paiva: LocalDate,
    tanaan: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    // Figma: 20 Regular, two lines with line height 22.
    val style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = STRIP_EDGE)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        viikko.forEach { day ->
            val selected = day == paiva
            val nimi =
                stringResource(R.string.paiva_ja_pvm, formatWeekday(day), formatDayMonth(day))
            val kuvaus = if (day == tanaan) stringResource(R.string.paiva_tanaan, nimi) else nimi
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .width(DAY_CELL)
                    .heightIn(min = DAY_CELL)
                    .clip(RoundedCornerShape(DAY_RADIUS))
                    .background(if (selected) LkPrimary else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(day) })
                    .semantics { contentDescription = kuvaus },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${formatWeekdayShort(day)}\n${day.dayOfMonth}",
                    style = style,
                    color = if (selected) Color.White else Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clearAndSetSemantics {}
                )
            }
        }
    }
}

/** "Tänään on torstai 18.12." / "klo 7.00" (live), or "Torstai 18.12." for another day. */
@Composable
private fun Otsikko(paiva: LocalDate, tanaan: LocalDate, nyt: LocalTime) {
    val text = if (paiva == tanaan) {
        val weekday = formatWeekday(paiva)
        stringResource(R.string.tanaan_on, weekday, formatDayMonth(paiva), formatTime(nyt))
    } else {
        stringResource(R.string.paiva_ja_pvm, formatWeekdayTitle(paiva), formatDayMonth(paiva))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Color.Black,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SIDE)
            .semantics { heading() }
    )
}

/** The day's doses by time; rows can be ticked only when [muokattavissa]. */
@Composable
private fun Annokset(
    ryhmat: List<AnnosRyhma>,
    muokattavissa: Boolean,
    onToggle: (AnnosRivi) -> Unit
) {
    Column(Modifier.padding(start = START, end = SIDE)) {
        ryhmat.forEachIndexed { index, ryhma ->
            if (index > 0) Spacer(Modifier.height(GROUP_GAP))
            RyhmaOtsikko(ryhma)
            ryhma.rivit.forEachIndexed { rowIndex, rivi ->
                Spacer(Modifier.height(if (rowIndex == 0) HEADING_TO_ROW else ROW_GAP))
                AnnosRow(rivi, enabled = muokattavissa, onToggle = { onToggle(rivi) })
            }
        }
    }
}

/** "klo 8.00" with the sun (before 11.00) or the moon (from 17.00). */
@Composable
private fun RyhmaOtsikko(ryhma: AnnosRyhma) {
    Row(
        modifier = Modifier.padding(start = 3.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when (ryhma.vuorokausi) {
            Vuorokausi.AAMU -> R.drawable.ic_sun
            Vuorokausi.ILTA -> R.drawable.ic_moon
            Vuorokausi.PAIVA -> null
        }
        if (icon != null) {
            // Decorative: the time says it.
            Image(painterResource(icon), contentDescription = null, Modifier.size(TIME_ICON))
            Spacer(Modifier.width(TIME_ICON_GAP))
        }
        Text(
            text = stringResource(R.string.klo_aika, formatTime(ryhma.aika)),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * One dose: its checkbox, "name **dose**" and, when missed, "ottamatta". The whole row is
 * the checkbox: tapping it calls [onToggle], and TalkBack reads the state instead of
 * "checked". A future day's row isn't [enabled]: it looks the same but can't be tapped.
 */
@Composable
private fun AnnosRow(rivi: AnnosRivi, enabled: Boolean, onToggle: () -> Unit) {
    val missed = rivi.tila == AnnoksenTila.OTTAMATTA
    val tila = when (rivi.tila) {
        AnnoksenTila.OTETTU -> stringResource(R.string.tila_otettu)
        AnnoksenTila.OTTAMATTA -> stringResource(R.string.ottamatta)
        AnnoksenTila.TULOSSA -> stringResource(R.string.tila_tulossa)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .toggleable(
                value = rivi.tila == AnnoksenTila.OTETTU,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onToggle() }
            )
            .semantics { stateDescription = tila },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(rivi.tila)
        Spacer(Modifier.width(CHECKBOX_TEXT_GAP))
        Column {
            Text(
                text = buildAnnotatedString {
                    append(rivi.nimi)
                    append(" ")
                    withStyle(SpanStyle(color = LkPrimary, fontWeight = FontWeight.Bold)) {
                        append(stringResource(R.string.annos, rivi.annosmaara, rivi.yksikko))
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black
            )
            if (missed) {
                // The row's state already says "ottamatta" to TalkBack.
                Text(
                    text = stringResource(R.string.ottamatta),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LkDanger,
                    modifier = Modifier.clearAndSetSemantics {}
                )
            }
        }
    }
}

/**
 * The Figma checkbox frame (fi-rs-stop). Taken: the navBar fill with the fi-rs-check.
 * Missed: a 5dp danger frame with "!" (colour isn't the only signal). Decorative:
 * TalkBack reads the row's state.
 */
@Composable
private fun Checkbox(tila: AnnoksenTila) {
    when (tila) {
        AnnoksenTila.OTETTU -> Box(Modifier.size(CHECKBOX).background(LkNavBar)) {
            Image(painterResource(R.drawable.ic_checkbox), contentDescription = null)
            Image(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.padding(start = CHECK_INSET, top = CHECK_INSET)
            )
        }
        AnnoksenTila.OTTAMATTA -> Box(
            // Decorative: TalkBack reads the row's state instead of the "!".
            modifier = Modifier
                .size(CHECKBOX)
                .border(MISSED_BORDER, LkDanger)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                style = MaterialTheme.typography.titleLarge,
                color = LkDanger
            )
        }
        AnnoksenTila.TULOSSA -> Image(
            painter = painterResource(R.drawable.ic_checkbox),
            contentDescription = null,
            modifier = Modifier.size(CHECKBOX)
        )
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Message(stringResource(R.string.ei_laakkeita_paivalle))
        Spacer(Modifier.height(32.dp))
        LkPrimaryButton(stringResource(R.string.lisaa_laake), onClick = onAdd)
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = SIDE)
    )
}
