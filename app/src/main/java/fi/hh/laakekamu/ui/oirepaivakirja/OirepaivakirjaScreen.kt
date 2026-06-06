package fi.hh.laakekamu.ui.oirepaivakirja

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fi.hh.laakekamu.R
import fi.hh.laakekamu.data.entity.Oiremerkinta
import fi.hh.laakekamu.ui.components.LkScreenTitle
import fi.hh.laakekamu.ui.components.LkSmallButton
import fi.hh.laakekamu.ui.format.formatDate
import fi.hh.laakekamu.ui.format.formatDayMonth
import fi.hh.laakekamu.ui.format.formatMonthTitle
import fi.hh.laakekamu.ui.format.formatTime
import fi.hh.laakekamu.ui.format.formatWeekday
import fi.hh.laakekamu.ui.format.formatWeekdayShort
import fi.hh.laakekamu.ui.theme.LaakeKamuTheme
import fi.hh.laakekamu.ui.theme.LkDanger
import fi.hh.laakekamu.ui.theme.LkMuted
import fi.hh.laakekamu.ui.theme.LkPrimary
import java.time.LocalDate
import java.time.YearMonth

// Figma (y minus the 24dp status bar Figma doesn't have): the title 18dp from the top,
// the calendar box 30dp under the title's line at x 26, 373dp wide at 412dp. Inside the box
// (from its outer edge, the 2dp border included): the arrows at (5, 13) and (55, 13), the
// weekday headers 8dp under them, the 7 columns about 49dp wide from x 12, and 20dp under the
// last row. Figma's rows are 40dp; ours are 48dp, the minimum touch target, which also leaves
// room for the day markers. The selected date keeps Figma's 40dp square.
private val TITLE_TOP = 18.dp
private val TITLE_TO_BOX = 30.dp
private val BOX_START = 26.dp
private val BOX_END = 13.dp
private val BOX_BORDER = 2.dp
private val ARROW = 50.dp
private val ARROWS_START = 5.dp
private val ARROWS_TOP = 13.dp
private val YEAR_END = 26.dp
private val HEADER_TO_WEEKDAYS = 8.dp
private val GRID_SIDE = 12.dp
private val GRID_BOTTOM = 20.dp
private val ROW_HEIGHT = 48.dp
private val SELECTED = 40.dp

// The ✓ of a complete day: the check icon on a white badge with a primary ring, over the
// top end corner of the date's 40dp square, so it reads the same on the selected square and
// stays clear of the digits (checked on the emulator).
private val BADGE = 18.dp
private val BADGE_RING = 1.5.dp
private val BADGE_ICON = 13.dp
private val BADGE_OFFSET = 5.dp

// The • of a day with a symptom: a dot centred under the date's square, in the gap
// between the rows, clear of the digits (checked on the emulator). A different shape from the ✓, so
// the two markers aren't told apart by colour.
private val DOT = 7.dp
private val DOT_DROP = 4.dp

// Figma: the lists 26dp under the box at x 52, 20sp; bullets indented as in
// Lääkelista, the text 30dp from the list's edge.
private val BOX_TO_LISTS = 26.dp

// Figma: an empty 20sp line between the taken doses and "Oireet".
private val LISTS_GAP = 27.dp
private val LISTS_START = 52.dp
private val LISTS_END = 16.dp
private val BULLET_INDENT = 13.dp
private val BULLET_WIDTH = 17.dp

// Figma: "Lisää" and "Muokkaa" at x 34 and 243, 56dp above the bottom bar.
private val BUTTONS_START = 34.dp
private val BUTTONS_END = 19.dp
private val BUTTONS_BOTTOM = 56.dp

/**
 * Oirepäiväkirja: the month calendar, Monday first, with Finnish month and weekday
 * names and a ✓ on complete days. ‹ › change the month; tapping a day selects it. Under
 * the calendar: the selected day's taken doses, deleted medications included.
 */
@Composable
fun OirepaivakirjaScreen(
    onAdd: (LocalDate) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: OirepaivakirjaViewModel = viewModel(factory = OirepaivakirjaViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(Unit) {
        viewModel.refresh()
        onStopOrDispose {}
    }
    OirepaivakirjaContent(
        state = state,
        onPrevious = viewModel::previousMonth,
        onNext = viewModel::nextMonth,
        onSelect = viewModel::selectDay,
        onAdd = { onAdd(state.valittu) },
        onDelete = viewModel::requestDelete,
        modifier = modifier
    )
    state.poistettava?.let { rivi ->
        PoistaOireDialog(
            rivi = rivi,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete
        )
    }

    // The deletion's result, shown once; kept for the recreated screen if the activity is
    // only rotating (as on Etusivu).
    val tulos = state.poistonTulos
    val poistettu = stringResource(R.string.oire_poistettu)
    val virhe = stringResource(R.string.virhe_oire_poisto)
    val activity = LocalActivity.current
    LaunchedEffect(tulos?.id) {
        if (tulos != null) {
            try {
                snackbarHostState.showSnackbar(if (tulos.onnistui) poistettu else virhe)
            } finally {
                if (activity?.isChangingConfigurations != true) {
                    viewModel.poistonTulosShown(tulos.id)
                }
            }
        }
    }
}

/**
 * The confirmation before a symptom is deleted for good (rule 7): "Poista" in danger,
 * "Peruuta" in primary, styled like the start-over dialog.
 */
@Composable
private fun PoistaOireDialog(rivi: OireRivi, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.poista_oire_otsikko),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = stringResource(
                    R.string.poista_oire_teksti,
                    rivi.kuvaus,
                    formatDayMonth(rivi.merkinta.aloituspv)
                ),
                fontSize = 18.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.poista),
                    color = LkDanger,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.peruuta),
                    color = LkPrimary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    )
}

@Composable
private fun OirepaivakirjaContent(
    state: OirepaivakirjaUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onAdd: () -> Unit,
    onDelete: (OireRivi) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        // The calendar and the lists scroll; the buttons stay put, as in Lääkelista.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Paivakirja(state, onPrevious, onNext, onSelect, onDelete)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = BUTTONS_START, end = BUTTONS_END, bottom = BUTTONS_BOTTOM),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LkSmallButton(stringResource(R.string.lisaa), onClick = onAdd, filled = true)
        }
    }
}

/** The title, the calendar and the selected day's lists. */
@Composable
private fun Paivakirja(
    state: OirepaivakirjaUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onDelete: (OireRivi) -> Unit
) {
    Column {
        LkScreenTitle(
            text = stringResource(R.string.nav_oirepaivakirja),
            icon = R.drawable.ic_calendar,
            modifier = Modifier.padding(top = TITLE_TOP)
        )
        Spacer(Modifier.height(TITLE_TO_BOX))
        Kalenteri(
            state = state,
            onPrevious = onPrevious,
            onNext = onNext,
            onSelect = onSelect,
            modifier = Modifier.padding(start = BOX_START, end = BOX_END)
        )
        Spacer(Modifier.height(BOX_TO_LISTS))
        Column(Modifier.padding(start = LISTS_START, end = LISTS_END)) {
            when {
                state.loadError -> Text(
                    text = stringResource(R.string.virhe_paivakirja),
                    style = MaterialTheme.typography.bodyLarge
                )
                state.loading -> Unit
                else -> {
                    Otetut(state.valittu, state.tanaan, state.otetut)
                    Spacer(Modifier.height(LISTS_GAP))
                    Oireet(state.oireet, onDelete)
                }
            }
        }
    }
}

/** "Tänään otetut lääkkeet" / "18.12. otetut lääkkeet" and the day's taken doses. */
@Composable
private fun Otetut(valittu: LocalDate, tanaan: LocalDate, otetut: List<OtettuRivi>) {
    Text(
        text = if (valittu == tanaan) {
            stringResource(R.string.otetut_tanaan)
        } else {
            stringResource(R.string.otetut_paivana, formatDayMonth(valittu))
        },
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.semantics { heading() }
    )
    if (otetut.isEmpty()) {
        Text(
            text = stringResource(R.string.ei_otettuja),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = BULLET_INDENT + BULLET_WIDTH)
        )
    }
    otetut.forEach { rivi ->
        Luettelorivi(
            buildAnnotatedString {
                append(rivi.nimi)
                append(" ")
                withStyle(SpanStyle(color = LkPrimary, fontWeight = FontWeight.Bold)) {
                    append(stringResource(R.string.annos, rivi.annosmaara, rivi.yksikko))
                }
                append(" ")
                append(stringResource(R.string.otettu_klo, formatTime(rivi.aika)))
            }
        )
    }
}

/** "Oireet" (Bold) and the selected day's symptoms, each with "sivuoire: X" when linked. */
@Composable
private fun Oireet(oireet: List<OireRivi>, onDelete: (OireRivi) -> Unit) {
    Text(
        text = stringResource(R.string.oireet_otsikko),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() }
    )
    if (oireet.isEmpty()) {
        Text(
            text = stringResource(R.string.ei_oireita),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = BULLET_INDENT + BULLET_WIDTH)
        )
    }
    oireet.forEach { oire ->
        Luettelorivi(
            text = AnnotatedString(oire.kuvaus),
            lisa = oire.sivuoire?.let { stringResource(R.string.sivuoire_rivi, it) },
            action = {
                // The edit selector is cut (out of scope); delete is on the row.
                val kuvaus = stringResource(R.string.poista_oire_kuvaus, oire.kuvaus)
                TextButton(
                    onClick = { onDelete(oire) },
                    modifier = Modifier.semantics { contentDescription = kuvaus }
                ) {
                    Text(
                        text = stringResource(R.string.poista),
                        color = LkDanger,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }
}

/**
 * A bulleted 20sp line with an optional 18sp [lisa] line under it (as Lääkelista's label),
 * read by TalkBack as one item without the bullet, and an optional [action] at its end
 * that TalkBack reaches separately.
 */
@Composable
private fun Luettelorivi(
    text: AnnotatedString,
    lisa: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = BULLET_INDENT),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(
                text = "•",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.width(BULLET_WIDTH).clearAndSetSemantics {}
            )
            Column {
                Text(text = text, style = MaterialTheme.typography.bodyLarge)
                if (lisa != null) {
                    Text(text = lisa, style = MaterialTheme.typography.bodyMedium, color = LkMuted)
                }
            }
        }
        action?.invoke()
    }
}

/** The calendar box: ‹ › with "Joulukuu 2025", the weekday headers and the month's days. */
@Composable
private fun Kalenteri(
    state: OirepaivakirjaUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .border(BOX_BORDER, Color.Black)
            .padding(bottom = GRID_BOTTOM)
    ) {
        KuukausiOtsikko(state.kuukausi, onPrevious, onNext)
        Spacer(Modifier.height(HEADER_TO_WEEKDAYS))
        Column(Modifier.padding(horizontal = GRID_SIDE)) {
            Viikonpaivat()
            Column(Modifier.selectableGroup()) {
                state.viikot.forEach { viikko ->
                    Row(Modifier.fillMaxWidth()) {
                        viikko.forEach { day ->
                            Paiva(
                                day = day,
                                selected = day == state.valittu,
                                today = day == state.tanaan,
                                valmis = day in state.valmiit,
                                oire = day in state.oirePaivat,
                                onSelect = onSelect,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * ‹ › and "Joulukuu 2025" (month 30sp, year 32sp, both Regular). TalkBack reads the month as a
 * heading and announces it when it changes.
 */
@Composable
private fun KuukausiOtsikko(kuukausi: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = ARROWS_START, top = ARROWS_TOP, end = YEAR_END),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Nuoli(R.drawable.ic_angle_left, stringResource(R.string.edellinen_kuukausi), onPrevious)
        Nuoli(R.drawable.ic_angle_right, stringResource(R.string.seuraava_kuukausi), onNext)
        Row(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatMonthTitle(kuukausi.atDay(1)),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = kuukausi.year.toString(),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Normal),
                color = Color.Black
            )
        }
    }
}

/** A month arrow: the 50dp Figma icon is the touch target. */
@Composable
private fun Nuoli(icon: Int, description: String, onClick: () -> Unit) {
    Icon(
        painter = painterResource(icon),
        contentDescription = description,
        tint = LkPrimary,
        modifier = Modifier
            .size(ARROW)
            .clickable(role = Role.Button, onClick = onClick)
    )
}

/** "ma ti ke to pe la su" (25sp). Decorative for TalkBack: each day reads its own weekday. */
@Composable
private fun Viikonpaivat() {
    // Any Monday works: only the weekday names are used.
    val monday = LocalDate.of(2024, 1, 1)
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
        (0L until 7L).forEach { offset ->
            Text(
                text = formatWeekdayShort(monday.plusDays(offset)),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * One date (25sp), or an empty cell outside the month. The selected date is Bold white on a
 * 40dp primary square. A complete day ([valmis]) has the Figma check icon in the square's
 * corner: a shape, so colour isn't the only signal. TalkBack reads
 * "torstai 18.12.2025", plus ", tänään" for today and ", kaikki lääkkeet otettu" when complete.
 */
@Composable
private fun Paiva(
    day: LocalDate?,
    selected: Boolean,
    today: Boolean,
    valmis: Boolean,
    oire: Boolean,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    if (day == null) {
        Spacer(modifier.height(ROW_HEIGHT))
        return
    }
    val nimi = stringResource(R.string.paiva_ja_pvm, formatWeekday(day), formatDate(day))
    val tanaan = if (today) stringResource(R.string.paiva_tanaan, nimi) else nimi
    val valmisKuvaus = if (valmis) stringResource(R.string.paiva_valmis, tanaan) else tanaan
    val kuvaus = if (oire) stringResource(R.string.paiva_oire, valmisKuvaus) else valmisKuvaus
    Box(
        modifier = modifier
            .heightIn(min = ROW_HEIGHT)
            .selectable(selected = selected, onClick = { onSelect(day) })
            .semantics { contentDescription = kuvaus },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(SELECTED, SELECTED)
                .background(if (selected) LkPrimary else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Color.White else Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.clearAndSetSemantics {}
            )
            if (valmis) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = BADGE_OFFSET, y = -BADGE_OFFSET)
                        .size(BADGE)
                        .background(Color.White, CircleShape)
                        .border(BADGE_RING, LkPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = LkPrimary,
                        modifier = Modifier.size(BADGE_ICON)
                    )
                }
            }
        }
        if (oire) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = DOT_DROP)
                    .size(DOT)
                    .background(LkPrimary, CircleShape)
            )
        }
    }
}

@Preview(widthDp = 412, heightDp = 917, showBackground = true)
@Composable
private fun OirepaivakirjaPreview() {
    val day = LocalDate.of(2025, 12, 18)
    LaakeKamuTheme {
        OirepaivakirjaContent(
            state = OirepaivakirjaUiState(
                tanaan = day,
                valittu = day,
                kuukausi = YearMonth.from(day),
                otetut = listOf(
                    OtettuRivi("A aamulääke", "5", "mg", 480),
                    OtettuRivi("B aamulääke", "10", "ml", 480)
                ),
                valmiit = setOf(day.minusDays(2), day.minusDays(1), day),
                oireet = listOf(
                    OireRivi(Oiremerkinta(1, "Huimaus", 1, day, null), "A aamulääke"),
                    OireRivi(Oiremerkinta(2, "Vatsakipu", null, day, null), null)
                ),
                oirePaivat = setOf(day.minusDays(1), day),
                loading = false
            ),
            onPrevious = {},
            onNext = {},
            onSelect = {},
            onAdd = {},
            onDelete = {}
        )
    }
}
