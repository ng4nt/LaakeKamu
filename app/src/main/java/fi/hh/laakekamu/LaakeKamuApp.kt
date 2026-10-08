package fi.hh.laakekamu

import android.app.Application
import android.os.StrictMode
import android.util.Log
import fi.hh.laakekamu.data.AppContainer
import fi.hh.laakekamu.reminders.Ilmoitukset
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class. It creates the [AppContainer] and the reminder channel and launches the
 * startup reschedule; nothing database-, Keystore- or prefs-related runs on the main thread
 * here.
 */
class LaakeKamuApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }
        container = AppContainer(this)
        Ilmoitukset.luoKanava(this)
        rescheduleOnStart()
    }

    /**
     * Sets the reminder alarms again on every start, only launched here: the
     * work runs in the app scope on the IO dispatcher. If the database can't be
     * opened, the UI shows its error screen; this only logs.
     */
    private fun rescheduleOnStart() {
        val scope = CoroutineScope(
            SupervisorJob() + container.ioDispatcher + CoroutineExceptionHandler { _, e ->
                Log.e(TAG, "Rescheduling on start failed", e)
            }
        )
        scope.launch { container.scheduler.rescheduleAll() }
    }

    /** Debug builds log disk access on the main thread and leaked closeables. */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
    }

    private companion object {
        const val TAG = "LaakeKamuApp"
    }
}
