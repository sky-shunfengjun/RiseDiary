package com.risediary.app

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.risediary.app.data.UserPreferences
import com.risediary.app.reminder.NotificationDestination
import com.risediary.app.ui.AppGateViewModel
import com.risediary.app.ui.RiseDiaryApp
import com.risediary.app.ui.theme.RiseDiaryTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.yukonga.miuix.kmp.basic.Surface
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var preferences: UserPreferences

    private val appGateViewModel: AppGateViewModel by viewModels()
    private val _notificationDestination =
        MutableStateFlow<NotificationDestination?>(null)
    private val notificationDestination = _notificationDestination.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveNotificationDestination(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            )
        )
        setContent {
            val themeMode by preferences.themeMode.collectAsStateWithLifecycle(initialValue = "system")
            val configuration = LocalConfiguration.current
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            }
            // The app theme can be forced dark/light independently of the system,
            // so keep the status/navigation bar icon style in sync with it.
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    }
                )
            }
            RiseDiaryTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color.Transparent
                ) {
                    RiseDiaryApp(
                        viewModel = appGateViewModel,
                        notificationDestination = notificationDestination,
                        onNotificationDestinationConsumed = { destination ->
                            if (_notificationDestination.compareAndSet(destination, null)) {
                                intent?.removeExtra(NotificationDestination.EXTRA_DESTINATION)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appGateViewModel.onAppReturnedToForeground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveNotificationDestination(intent)
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            appGateViewModel.onAppMovedToBackground()
        }
        super.onStop()
    }

    private fun receiveNotificationDestination(intent: Intent?) {
        _notificationDestination.value = NotificationDestination.fromStoredValue(
            intent?.getStringExtra(NotificationDestination.EXTRA_DESTINATION)
        )
    }
}
