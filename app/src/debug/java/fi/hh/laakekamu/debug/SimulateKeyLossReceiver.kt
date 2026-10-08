package fi.hh.laakekamu.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fi.hh.laakekamu.data.SqlCipherKey
import kotlin.concurrent.thread

/**
 * Debug builds only: "simulate key loss". Deletes the database's Keystore
 * key, so the next start of the app can't open the database and shows "Tietoja ei voitu avata".
 * The data stays until the user confirms "Aloita alusta". Only the shell may send it:
 * `adb shell am broadcast -n fi.hh.laakekamu/.debug.SimulateKeyLossReceiver`, then
 * `adb shell am force-stop fi.hh.laakekamu` and start the app again.
 */
class SimulateKeyLossReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        // The Keystore is I/O: off the main thread.
        thread(name = "SimulateKeyLoss") {
            try {
                SqlCipherKey(context).deleteKeystoreKey()
                Log.w(TAG, "Debug: deleted the database's Keystore key")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SimulateKeyLoss"
    }
}
