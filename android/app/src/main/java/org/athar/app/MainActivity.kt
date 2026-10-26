package org.athar.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.athar.app.auth.*
import org.athar.app.core.ui.*
import org.athar.app.notifications.NotificationDeepLinks
import org.athar.app.notifications.NotificationLaunch

class MainActivity : AppCompatActivity() {
    private var pendingNotificationLaunch by mutableStateOf<NotificationLaunch?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        pendingNotificationLaunch = NotificationDeepLinks.fromIntent(intent)
        if (AppCompatDelegate.getApplicationLocales().isEmpty) AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
        enableEdgeToEdge()
        val graph = (application as AtharApplication).graph
        setContent {
            val vm: AppViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(graph) as T
            })
            val settings by vm.settings.collectAsStateWithLifecycle()
            val auth by vm.auth.collectAsStateWithLifecycle()
            AtharTheme(settings?.theme ?: "light") {
                Surface {
                    when {
                        settings == null || auth is AuthState.Restoring -> LoadingScreen()
                        settings?.onboardingComplete == false -> OnboardingScreen(vm::completeOnboarding)
                        auth is AuthState.SignedOut -> AuthScreen(vm)
                        auth is AuthState.Pending -> key((auth as AuthState.Pending).user.id) { VerificationScreen(vm, auth as AuthState.Pending) }
                        auth is AuthState.SignedIn -> key((auth as AuthState.SignedIn).user.id) {
                            MainShell(
                                vm = vm,
                                auth = auth as AuthState.SignedIn,
                                notificationLaunch = pendingNotificationLaunch,
                                onNotificationLaunchConsumed = { launch ->
                                    if (pendingNotificationLaunch == launch) pendingNotificationLaunch = null
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationLaunch = NotificationDeepLinks.fromIntent(intent)
    }
}
