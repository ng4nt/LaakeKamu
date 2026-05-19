package fi.hh.laakekamu.ui.oirepaivakirja

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fi.hh.laakekamu.R
import fi.hh.laakekamu.ui.components.LkScreenTitle
import fi.hh.laakekamu.ui.format.formatDate
import fi.hh.laakekamu.ui.format.formatMonthTitle
import fi.hh.laakekamu.ui.format.formatWeekday
import fi.hh.laakekamu.ui.format.formatWeekdayShort
import fi.hh.laakekamu.ui.theme.LaakeKamuTheme
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

/**
 * Oirepäiväkirja: the month calendar, Monday first, with Finnish month and weekday
 * names. ‹ › change the month; tapping a day selects it.
 */
@Composable
fun OirepaivakirjaScreen(
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
        modifier = modifier
    )
}

@Composable
private fun OirepaivakirjaContent(
    state: OirepaivakirjaUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
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
 * 40dp primary square. TalkBack reads "torstai 18.12.2025", plus ", tänään" for today.
 */
@Composable
private fun Paiva(
    day: LocalDate?,
    selected: Boolean,
    today: Boolean,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    if (day == null) {
        Spacer(modifier.height(ROW_HEIGHT))
        return
    }
    val nimi = stringResource(R.string.paiva_ja_pvm, formatWeekday(day), formatDate(day))
    val kuvaus = if (today) stringResource(R.string.paiva_tanaan, nimi) else nimi
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
        }
    }
}

@Preview(widthDp = 412, heightDp = 917, showBackground = true)
@Composable
private fun OirepaivakirjaPreview() {
    val day = LocalDate.of(2025, 12, 18)
    LaakeKamuTheme {
        OirepaivakirjaContent(
            state = OirepaivakirjaUiState(day, day, YearMonth.from(day)),
            onPrevious = {},
            onNext = {},
            onSelect = {}
        )
    }
}
