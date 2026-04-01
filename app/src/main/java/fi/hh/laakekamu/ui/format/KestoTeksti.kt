package fi.hh.laakekamu.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fi.hh.laakekamu.R
import fi.hh.laakekamu.domain.Kesto

/** The Finnish duration label, e.g. "vielä 14 pv" or "alkaa 20.10.". */
@Composable
fun kestoTeksti(kesto: Kesto): String = when (kesto) {
    is Kesto.Alkaa -> stringResource(R.string.kesto_alkaa, formatDayMonth(kesto.date))
    is Kesto.Jaljella -> stringResource(R.string.kesto_jaljella, kesto.days)
    Kesto.Paattynyt -> stringResource(R.string.kesto_paattynyt)
    is Kesto.Kaytossa -> stringResource(R.string.kesto_kaytossa, kesto.days)
}
