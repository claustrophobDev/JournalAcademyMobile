package com.claustrophob.journal

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.claustrophob.journal.ui.Root
import com.claustrophob.journal.ui.theme.JournalTheme
import com.claustrophob.journal.work.Notifier

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // С кнопочной навигацией Samsung рисует под кнопками белую полосу.
        // Убираем, снизу и так либо панель вкладок, либо фон.
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        Notifier.ensureChannels(this)
        pendingRoute = intent?.getStringExtra(Notifier.EXTRA_ROUTE)

        setContent {
            val settings by container.settings.settings.collectAsState()
            val route = pendingRoute

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !Notifier.canNotify(this@MainActivity)
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            JournalTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                Root(
                    startRoute = remember(route) { route },
                    onRouteConsumed = { pendingRoute = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intent.getStringExtra(Notifier.EXTRA_ROUTE)
    }
}
