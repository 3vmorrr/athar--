package org.athar.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.athar.app.auth.*
import org.athar.app.core.ui.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (AppCompatDelegate.getApplicationLocales().isEmpty) AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
        enableEdgeToEdge()
        val graph = (application as AtharApplication).graph
        setContent {
            val vm: AppViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(graph) as T
            })
            val settings by vm.settings.collectAsStateWithLifecycle()
            val auth by vm.auth.collectAsStateWithLifecycle()
            val deletionOwners by graph.deletionStore.blockedOwners.collectAsStateWithLifecycle()
            AtharTheme(settings?.theme ?: "light") {
                Surface {
                    when {
                        settings == null || auth is AuthState.Restoring -> LoadingScreen()
                        auth is AuthState.RestoreFailed -> SessionRestoreFailure(vm::restoreSession)
                        settings?.onboardingComplete == false -> OnboardingScreen(vm::completeOnboarding)
                        auth is AuthState.SignedOut -> AuthScreen(vm)
                        auth is AuthState.Pending -> key((auth as AuthState.Pending).user.id) { VerificationScreen(vm, auth as AuthState.Pending) }
                        auth is AuthState.SignedIn -> key((auth as AuthState.SignedIn).user.id) {
                            val actor=auth as AuthState.SignedIn
                            if(actor.user.id in deletionOwners)AccountDeletionRecoveryScreen(vm,actor.user.id)else MainShell(vm,actor)
                        }
                    }
                }
            }
        }
    }
}
