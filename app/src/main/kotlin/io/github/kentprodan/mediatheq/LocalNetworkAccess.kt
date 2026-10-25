package io.github.kentprodan.mediatheq

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.kentprodan.mediatheq.core.designsystem.component.MqEmptyState
import io.github.kentprodan.mediatheq.core.designsystem.icon.MqIcons

/**
 * Shows [content] once the app may reach devices on the local network. Since Android 17 an app targeting SDK 37 needs
 * ACCESS_LOCAL_NETWORK for that; without it every connection to a home server just times out (#58). The permission is
 * read again on every resume, so a change in the system settings applies when the user comes back.
 */
@Composable
fun LocalNetworkGate(
    requiresPermission: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN,
    content: @Composable () -> Unit,
) {
    if (!requiresPermission) return content()
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasLocalNetworkAccess()) }
    // After a refusal the system may stop asking; then only the app settings can turn it on.
    var deniedForGood by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        granted = context.hasLocalNetworkAccess()
        onPauseOrDispose {}
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            granted = isGranted
            deniedForGood = !isGranted && context.activity?.shouldShowRequestPermissionRationale(PERMISSION) == false
        }
    if (granted) {
        content()
    } else {
        LocalNetworkRequired(
            deniedForGood = deniedForGood,
            onAllow = { launcher.launch(PERMISSION) },
            onOpenSettings = { context.openAppSettings() },
        )
    }
}

/** Why the app needs the local network, with the one action that can grant it. */
@Composable
fun LocalNetworkRequired(
    deniedForGood: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        MqEmptyState(
            icon = MqIcons.Lan,
            title = stringResource(R.string.local_network_title),
            body =
                stringResource(
                    if (deniedForGood) R.string.local_network_denied_body else R.string.local_network_body,
                ),
            actionLabel =
                stringResource(
                    if (deniedForGood) R.string.local_network_open_settings else R.string.local_network_allow,
                ),
            onAction = if (deniedForGood) onOpenSettings else onAllow,
        )
    }
}

private const val PERMISSION = Manifest.permission.ACCESS_LOCAL_NETWORK

private fun Context.hasLocalNetworkAccess() =
    ContextCompat.checkSelfPermission(this, PERMISSION) == PackageManager.PERMISSION_GRANTED

private val Context.activity: Activity?
    get() = generateSequence(this) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
