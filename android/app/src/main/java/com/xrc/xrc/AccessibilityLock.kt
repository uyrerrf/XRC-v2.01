// ============================================================
// FILE: android/app/src/main/java/com/xrc/xrc/AccessibilityLock.kt
// ============================================================
package com.xrc.xrc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * AccessibilityLock — Full-screen overlay that blocks the user
 * from using the device until AccessibilityService is enabled.
 *
 * Shows instructions and a button to open accessibility settings.
 * On Android 12+, uses the overlay permission first, then A11Y.
 */
@Composable
fun AccessibilityLock(
    modifier: Modifier = Modifier,
    permissionState: String = "checking",
    onPermissionGranted: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .background(Color(0xFF0B0E14))
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Status indicator
            when (permissionState) {
                "overlay" -> {
                    Text(
                        text = "Overlay Permission Required",
                        color = Color(0xFF00E5FF),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Grant overlay permission to continue setup",
                        color = Color(0xFF888888),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
                "service" -> {
                    CircularProgressIndicator(
                        color = Color(0xFF00E5FF),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Starting service...",
                        color = Color(0xFF888888),
                        fontSize = 14.sp
                    )
                }
                "accessibility" -> {
                    Text(
                        text = "Accessibility Service Required",
                        color = Color(0xFFFF1744),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Enable XRC Accessibility Service to continue.\n\n" +
                                "1. Tap the button below\n" +
                                "2. Find \"XRC\" in the list\n" +
                                "3. Toggle the switch ON\n" +
                                "4. Confirm the dialog",
                        color = Color(0xFFAAAAAA),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Start,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onPermissionGranted,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor = Color(0xFF0B0E14)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = "Open Accessibility Settings",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Check: Settings → Accessibility → Installed Apps → XRC",
                        color = Color(0xFF666666),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    CircularProgressIndicator(
                        color = Color(0xFF00E5FF),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Checking permissions...",
                        color = Color(0xFF888888),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "XRC v1.0",
                color = Color(0xFF333333),
                fontSize = 11.sp
            )
        }
    }
}
