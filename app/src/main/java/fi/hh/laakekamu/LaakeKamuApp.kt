package fi.hh.laakekamu

import android.app.Application
import android.os.StrictMode
import fi.hh.laakekamu.data.AppContainer
import fi.hh.laakekamu.reminders.Ilmoitukset

/**
 * Application class. It creates the [AppContainer] and the reminder channel; nothing
 * database-, Keystore- or prefs-related runs on the main thread here.
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
}
