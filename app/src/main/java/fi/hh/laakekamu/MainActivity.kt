package fi.hh.laakekamu

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import fi.hh.laakekamu.ui.LaakeKamuRoot
import fi.hh.laakekamu.ui.theme.LaakeKamuTheme
import fi.hh.laakekamu.ui.tietokanta.TietokantaPortti

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash comes from Theme.LaakeKamu.Starting; this switches to the
        // app theme. It must run before super.onCreate.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // The app is always light, so the bar icons must stay dark even in system dark mode.
        // The default (SystemBarStyle.auto) would follow the system mode instead.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            LaakeKamuTheme {
                TietokantaPortti {
                    LaakeKamuRoot()
                }
            }
        }
    }
}
