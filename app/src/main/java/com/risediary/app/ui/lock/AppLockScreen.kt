package com.risediary.app.ui.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.withResumed
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.risediary.app.R
import com.risediary.app.ui.components.LiquidGlassButton
import com.risediary.app.ui.theme.CardBlue

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/** Full-screen PIN entry with optional fingerprint. */
@Composable
fun AppLockScreen(
    mode: LockMode = LockMode.VERIFY,
    onDone: () -> Unit,
    onCancel: (() -> Unit)? = null,  // null = no cancel button
    vm: AppLockViewModel = hiltViewModel()
) {
    LaunchedEffect(mode) { vm.init(mode) }

    val pin by vm.pin.collectAsStateWithLifecycle()
    val title by vm.title.collectAsStateWithLifecycle()
    val error by vm.errorMessage.collectAsStateWithLifecycle()
    val lockout by vm.lockoutRemaining.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val biometricEnabled by vm.biometricUnlockEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity() as? FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    var autoBiometricRequested by remember(mode) { mutableStateOf(false) }
    val lockBackground = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.98f),
            Color(0xFF315B91),
            Color(0xFF14223E)
        )
    )
    // Capture the gradient only so glass controls never capture themselves.
    val lockBackdrop = rememberLayerBackdrop {
        drawRect(brush = lockBackground)
    }

    // Vibrate on error
    LaunchedEffect(error) {
        if (error != null) {
            vibrate(context)
        }
    }

    // Navigate away on done
    LaunchedEffect(done) {
        if (done) {
            vm.consumeDone()
            onDone()
        }
    }

    LaunchedEffect(ready, biometricEnabled, mode, activity, lifecycleOwner) {
        val resumedActivity = activity ?: return@LaunchedEffect
        if (
            ready &&
            biometricEnabled &&
            mode == LockMode.VERIFY &&
            vm.biometricAvailable &&
            !autoBiometricRequested
        ) {
            lifecycleOwner.lifecycle.withResumed {
                if (!autoBiometricRequested) {
                    autoBiometricRequested = true
                    vm.authenticateWithBiometric(resumedActivity)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(lockBackground),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(lockBackdrop)
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(32.dp)
                .systemBarsPadding()
        ) {
            // Cancel button (top-right, for onboarding skip)
            if (onCancel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) {
                        Text("取消", color = Color.White.copy(alpha = 0.78f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Image(
                painter = painterResource(R.drawable.app_icon),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(25.dp))
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            // PIN dots
            if (lockout > 0) {
                // Lockout countdown
                Text(
                    text = "请等待 ${lockout} 秒",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                PinDots(
                    count = 4,
                    filled = pin.length,
                    isError = error != null
                )
            }

            // Error message
            if (error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Numeric keypad
            if (lockout <= 0 && ready) {
                NumericKeypad(
                    onDigit = { vm.onDigit(it) },
                    onDelete = { vm.onDelete() },
                    onLongDelete = vm::clearInput,
                    backdrop = lockBackdrop
                )
            } else if (!ready) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Fingerprint
            if (
                ready &&
                biometricEnabled &&
                vm.biometricAvailable &&
                mode == LockMode.VERIFY
            ) {
                IconButton(
                    onClick = {
                        activity?.let { vm.authenticateWithBiometric(it) }
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = "指纹验证",
                        modifier = Modifier.size(40.dp),
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "指纹验证",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** 4 circular dots showing PIN progress. */
@Composable
private fun PinDots(count: Int, filled: Int, isError: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            val filledState = i < filled
            val dotColor = when {
                isError -> MaterialTheme.colorScheme.error
                filledState -> Color.White
                else -> Color.White.copy(alpha = 0.28f)
            }
            val borderColor = if (!filledState && !isError) {
                Color.White.copy(alpha = 0.62f)
            } else {
                Color.Transparent
            }

            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(dotColor)
                    .then(
                        if (borderColor != Color.Transparent) {
                            Modifier.border(1.5.dp, borderColor, CircleShape)
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (filledState && !isError) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

/** 3×4 numeric keypad: 1-9, blank, 0, backspace. */
@Composable
private fun NumericKeypad(
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onLongDelete: () -> Unit,
    backdrop: Backdrop
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { label ->
                    when {
                        label.isEmpty() -> {
                            Spacer(modifier = Modifier.size(76.dp))
                        }
                        label == "⌫" -> {
                            KeyButton(
                                label = "",
                                icon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "删除",
                                        modifier = Modifier.size(26.dp),
                                        tint = Color.White
                                    )
                                },
                                onClick = onDelete,
                                onLongClick = onLongDelete,
                                backdrop = backdrop
                            )
                        }
                        else -> {
                            KeyButton(
                                label = label,
                                onClick = { onDigit(label.toInt()) },
                                backdrop = backdrop
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyButton(
    label: String,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    backdrop: Backdrop
) {
    Box(
        modifier = Modifier.size(76.dp),
        contentAlignment = Alignment.Center
    ) {
        // The moving surface stays behind the content: a long press still has
        // the official Demo glow, but it cannot stretch or wash out the digit.
        LiquidGlassButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = Modifier.fillMaxSize(),
            tint = Color.White.copy(alpha = 0.025f),
            height = 76.dp,
            horizontalPadding = 0.dp,
            onLongClick = onLongClick,
            highlightIntensity = 0.42f,
            highlightRadiusMultiplier = 0.82f,
            pressExpansion = 2.5.dp
        ) {}

        if (icon != null) {
            icon()
        } else {
            Text(
                text = label,
                fontSize = 29.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = Color.White
            )
        }
    }
}

private fun vibrate(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(
                VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }
    } catch (_: Exception) { }
}
