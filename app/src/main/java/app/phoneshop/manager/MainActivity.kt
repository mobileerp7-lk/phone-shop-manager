package app.phoneshop.manager

import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import app.phoneshop.manager.ui.AppRoot
import app.phoneshop.manager.ui.LocalReduceMotion
import app.phoneshop.manager.ui.PhoneShopTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val repo = (application as PhoneShopApp).repo
        // People who turn animations off in phone settings get a calm, motion-free app.
        val reduceMotion =
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        setContent {
            PhoneShopTheme {
                CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                    AppRoot(repo)
                }
            }
        }
    }
}
