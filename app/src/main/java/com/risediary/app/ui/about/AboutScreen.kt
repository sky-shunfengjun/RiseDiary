package com.risediary.app.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.risediary.app.BuildConfig
import com.risediary.app.R
import com.risediary.app.ui.components.LiquidGlassButton
import com.risediary.app.ui.components.SecondaryPageScaffold
import com.risediary.app.ui.update.UpdateAvailableDialog
import com.risediary.app.ui.update.UpdateStatusDialog
import com.risediary.app.ui.update.UpdateStatusDialogState
import com.risediary.app.update.UpdateCheckState
import com.risediary.app.update.UpdateViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import com.risediary.app.ui.icons.AppIcons

@Composable
fun AboutScreen(
    navController: NavController,
    updateViewModel: UpdateViewModel = hiltViewModel()
) {
    val uriHandler = LocalUriHandler.current
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    var showStatusDialog by remember { mutableStateOf(false) }
    val availableRelease = (updateState as? UpdateCheckState.Available)?.release
    val isChecking = updateState is UpdateCheckState.Checking
    val updateButtonDescription = stringResource(R.string.update_button_content_description)

    SecondaryPageScaffold(
        title = "关于",
        onBack = { navController.navigateUp() },
        bottomAction = { backdrop ->
            LiquidGlassButton(
                onClick = {
                    showStatusDialog = true
                    updateViewModel.checkForUpdate(force = true)
                },
                backdrop = backdrop,
                enabled = !isChecking,
                isInteractive = !isChecking,
                tint = MiuixTheme.colorScheme.primary.copy(alpha = 0.075f),
                height = 52.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = updateButtonDescription
                    }
            ) {
                Icon(AppIcons.Refresh, contentDescription = null)
                Text(
                    if (isChecking) {
                        stringResource(R.string.update_checking)
                    } else {
                        stringResource(R.string.update_check_button)
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .scrollEndHaptic()
                .overScrollVertical(),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            overscrollEffect = null
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(R.drawable.app_icon),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(24.dp))
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "起飞日记",
                            fontSize = MiuixTheme.textStyles.title2.fontSize
                        )
                        Text(
                            "版本 ${BuildConfig.VERSION_NAME}",
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        AboutItem(
                            stringResource(R.string.about_usage_title),
                            stringResource(R.string.usage_disclaimer)
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        AboutItem(
                            "数据隐私",
                            stringResource(R.string.about_privacy_content)
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        AboutItem(
                            "开源组件",
                            "本应用使用 Jetpack Compose、Miuix、Room、DataStore、Hilt、AndroidX 与 AndroidLiquidGlass。"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        AboutLink(
                            title = stringResource(R.string.about_github_title),
                            url = stringResource(R.string.about_github_url),
                            contentDescription = stringResource(R.string.about_github_content_description),
                            onClick = {
                                runCatching {
                                    uriHandler.openUri("https://github.com/sky-shunfengjun/RiseDiary")
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        AboutLink(
                            title = stringResource(R.string.about_qq_group_title),
                            url = stringResource(R.string.about_qq_group_url),
                            contentDescription = stringResource(R.string.about_qq_group_content_description),
                            showUrl = false,
                            onClick = {
                                runCatching {
                                    uriHandler.openUri("https://qm.qq.com/q/Z3XTPXXEEW")
                                }
                            }
                        )
                    }
                }
            }

            item {
                Text(
                    "专注记录，也尊重每一份隐私",
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (availableRelease != null) {
        UpdateAvailableDialog(
            currentVersion = updateViewModel.currentVersion,
            release = availableRelease,
            onDismiss = updateViewModel::dismiss,
            onOpenRelease = { releaseUrl ->
                updateViewModel.dismiss()
                runCatching { uriHandler.openUri(releaseUrl) }
            }
        )
    } else if (showStatusDialog) {
        val status = when (updateState) {
            UpdateCheckState.UpToDate -> UpdateStatusDialogState.UP_TO_DATE
            UpdateCheckState.Failed -> UpdateStatusDialogState.FAILED
            else -> null
        }
        if (status != null) {
            UpdateStatusDialog(
                currentVersion = updateViewModel.currentVersion,
                state = status,
                onDismiss = {
                    showStatusDialog = false
                    updateViewModel.dismiss()
                }
            )
        }
    }
}

@Composable
private fun AboutItem(title: String, desc: String) {
    Text(
        title,
        fontSize = MiuixTheme.textStyles.body1.fontSize,
        color = MiuixTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        desc,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
}

@Composable
private fun AboutLink(
    title: String,
    url: String,
    contentDescription: String,
    showUrl: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(vertical = 4.dp)
    ) {
        Text(
            title,
            fontSize = MiuixTheme.textStyles.body1.fontSize,
            color = MiuixTheme.colorScheme.onSurface
        )
        if (showUrl) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                url,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.primary
            )
        }
    }
}
