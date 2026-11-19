package com.kanime.app.ui.notify

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kanime.app.R
import com.kanime.app.data.notify.EpisodeAlertPreferences
import com.kanime.app.data.notify.EpisodeAlerts

/**
 * Shared on/off logic for new-episode alerts (Settings switch + Schedule screen chip):
 * asks for the notification permission on Android 13+ before turning alerts on, and starts or
 * stops the background check.
 *
 * @return current state and a setter to call with the wanted state.
 */
@Composable
fun rememberEpisodeAlertsToggle(): Pair<Boolean, (Boolean) -> Unit> {
    val context = LocalContext.current
    val enabled by EpisodeAlertPreferences.enabledFlow.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            EpisodeAlertPreferences.setEnabled(true)
            EpisodeAlerts.schedule(context)
        } else {
            Toast.makeText(context, context.getString(R.string.alerts_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    val setEnabled: (Boolean) -> Unit = { wanted ->
        if (!wanted) {
            EpisodeAlertPreferences.setEnabled(false)
            EpisodeAlerts.cancel(context)
        } else if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            EpisodeAlertPreferences.setEnabled(true)
            EpisodeAlerts.schedule(context)
        }
    }
    return enabled to setEnabled
}
