package com.risediary.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.risediary.app.R
import com.risediary.app.ui.settings.SettingsThemeItem
import com.risediary.app.ui.theme.CardBlue
import com.risediary.app.ui.theme.CardGreen
import com.risediary.app.ui.theme.RiseCard
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ProfileOnboardingPage(username: String, onUsernameChange: (String) -> Unit) {
    SetupPage(Icons.Default.Person, stringResource(R.string.onboarding_profile_title), stringResource(R.string.onboarding_profile_subtitle)) {
        RiseCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = stringResource(R.string.settings_username),
                    useLabelAsPlaceholder = false
                )
                Text(
                    stringResource(R.string.onboarding_profile_support),
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                InfoRow(Icons.Default.Storage, stringResource(R.string.onboarding_local_data_title), stringResource(R.string.onboarding_local_data_summary), CardGreen)
                InfoRow(Icons.Default.Backup, stringResource(R.string.onboarding_backup_title), stringResource(R.string.onboarding_backup_summary), CardBlue)
            }
        }
    }
}

@Composable
internal fun ThemeOnboardingPage(themeMode: String, backdrop: Backdrop, onThemeSelected: (String) -> Unit) {
    SetupPage(Icons.Default.Dashboard, stringResource(R.string.onboarding_theme_title), stringResource(R.string.onboarding_theme_subtitle)) {
        RiseCard(modifier = Modifier.fillMaxWidth()) {
            SettingsThemeItem(Icons.Default.Dashboard, stringResource(R.string.settings_theme), themeMode, onThemeSelected)
        }
        MiniDashboardPreview()
        Text(
            stringResource(R.string.onboarding_theme_hint),
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MiniDashboardPreview() {
    RiseCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text(
                        stringResource(R.string.onboarding_preview_greeting),
                        fontSize = MiuixTheme.textStyles.title4.fontSize,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.onboarding_preview_summary),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.FlightTakeoff,
                            null,
                            tint = MiuixTheme.colorScheme.primary
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PreviewMetric("7", stringResource(R.string.onboarding_preview_week), Modifier.weight(1f))
                PreviewMetric("12m", stringResource(R.string.onboarding_preview_average), Modifier.weight(1f))
                PreviewMetric("3", stringResource(R.string.onboarding_preview_badges), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PreviewMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    ) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.primary)
            Text(label, fontSize = MiuixTheme.textStyles.footnote1.fontSize)
        }
    }
}
