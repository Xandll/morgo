package io.qzz.xandll.morgo.presentation

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.data.update.UpdateInstaller
import io.qzz.xandll.morgo.presentation.home.HomeScreen
import io.qzz.xandll.morgo.presentation.update.UpdateDialog
import io.qzz.xandll.morgo.presentation.onboarding.OnboardingScreen
import io.qzz.xandll.morgo.presentation.settings.SettingsScreen
import io.qzz.xandll.morgo.presentation.theme.MorgoTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var updateInstaller: UpdateInstaller

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val openOverride = intent?.getBooleanExtra(EXTRA_OPEN_OVERRIDE, false) == true
        setContent {
            val settings by settingsRepository.settingsFlow.collectAsState()
            val offer by settingsRepository.updateOffer.collectAsState()
            val installStatus by updateInstaller.status.collectAsState()
            MorgoTheme(dynamicColor = settings.useDynamicColor) {
                val visibleOffer = offer?.takeIf { settings.update.enabled && !it.dismissed }
                if (visibleOffer != null) {
                    UpdateDialog(
                        offer = visibleOffer,
                        canInstall = updateInstaller.canInstallPackages(),
                        status = installStatus,
                        onLater = { settingsRepository.dismissUpdateOffer() },
                        onInstall = { updateInstaller.enqueue(visibleOffer) },
                        onAllowUnknown = { startActivity(updateInstaller.unknownSourcesIntent()) }
                    )
                }
                MainScreen(
                    showOnboarding = !settings.onboardingComplete && settings.webUntis.username.isBlank(),
                    openOverride = openOverride,
                    onOnboardingFinished = { }
                )
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_OVERRIDE = "open_override"
    }
}

@Composable
fun MainScreen(
    showOnboarding: Boolean,
    openOverride: Boolean,
    onOnboardingFinished: () -> Unit
) {
    val navController = rememberNavController()
    val start = if (showOnboarding) "onboarding" else "home"
    NavHost(
        navController = navController,
        startDestination = start,
        modifier = Modifier.fillMaxSize()
    ) {
        composable("onboarding") {
            OnboardingScreen(onFinished = {
                onOnboardingFinished()
                navController.navigate("home") {
                    popUpTo("onboarding") { inclusive = true }
                }
            })
        }
        composable("home") {
            HomeScreen(
                onNavigateToSettings = { navController.navigate("settings") },
                openOverride = openOverride
            )
        }
        composable("settings") {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
