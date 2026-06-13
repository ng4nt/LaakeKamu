package fi.hh.laakekamu.reminders

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.hh.laakekamu.data.AnnosRepository
import fi.hh.laakekamu.data.dao.MuistutettavaAnnos
import fi.hh.laakekamu.data.entity.Laake
import fi.hh.laakekamu.data.entity.Muistutus
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** ticking a dose in the app removes its notification; unticking doesn't. */
@RunWith(AndroidJUnit4::class)
class KuittaavaAnnosRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val today = LocalDate.of(2025, 12, 18)
    private val ticks = mutableListOf<Pair<Long, Boolean>>()

    private val inner = object : AnnosRepository {
        override fun observeLaakkeet(): Flow<List<Laake>> = emptyFlow()
        override fun observeMuistutukset(): Flow<List<Muistutus>> = emptyFlow()
        override fun observeOtetut(paiva: LocalDate): Flow<Set<Long>> = emptyFlow()
        override suspend fun setOtettu(muistutusId: Long, paiva: LocalDate, otettu: Boolean) {
            ticks += muistutusId to otettu
        }
    }

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
        Ilmoitukset.luoKanava(context)
    }

    @After
    fun tearDown() {
        notifications.cancelAll()
    }

    private fun post(id: Long) = Ilmoitukset.naytaAnnos(
        context,
        MuistutettavaAnnos(id, 8 * 60, today, null, "A aamulääke", "5", "mg", today, null),
        today
    )

    private fun shownIds() = notifications.activeNotifications.map { it.id }.toSet()

    @Test
    fun tickingInTheApp_removesThatDosesNotificationOnly() = runBlocking {
        post(101)
        post(102)
        val repository = KuittaavaAnnosRepository(inner, context)

        repository.setOtettu(101, today, otettu = true)
        assertEquals(setOf(102), shownIds())

        repository.setOtettu(102, today, otettu = false)
        assertEquals(setOf(102), shownIds())
        assertEquals(listOf(101L to true, 102L to false), ticks)
    }
}
