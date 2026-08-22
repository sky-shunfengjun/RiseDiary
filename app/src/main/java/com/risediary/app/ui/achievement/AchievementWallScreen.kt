package com.risediary.app.ui.achievement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.risediary.app.ui.components.SecondaryPageScaffold
import com.risediary.app.ui.theme.CardBlue
import com.risediary.app.ui.theme.CardGreen
import com.risediary.app.ui.theme.CardOrange
import com.risediary.app.ui.theme.CardPurple
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AchievementWallScreen(
    navController: NavController,
    vm: AchievementWallViewModel = hiltViewModel()
) {
    val unlocked by vm.unlockedAchievements.collectAsStateWithLifecycle()
    val progress by vm.progressMap.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refresh() }

    val unlockedKeys = unlocked.map { it.achievementKey }.toSet()
    val unlockedMap = unlocked.associateBy { it.achievementKey }

    SecondaryPageScaffold(
        title = "成就墙",
        onBack = { navController.navigateUp() }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .scrollEndHaptic()
                .overScrollVertical(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            overscrollEffect = null
        ) {
            items(vm.allDefinitions) { def ->
                val isUnlocked = def.key in unlockedKeys
                val achievement = unlockedMap[def.key]
                val prog = progress[def.key] ?: 0f
                val accentColor = when (def.category) {
                    "milestone" -> CardOrange
                    "record" -> CardPurple
                    "streak" -> CardGreen
                    else -> CardBlue
                }

                AchievementCard(
                    def = def,
                    isUnlocked = isUnlocked,
                    unlockedDate = achievement?.unlockedAt,
                    progress = prog,
                    accentColor = accentColor
                )
            }
        }
    }
}

@Composable
private fun AchievementCard(
    def: AchievementDef,
    isUnlocked: Boolean,
    unlockedDate: Long?,
    progress: Float,
    accentColor: Color
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { SimpleDateFormat("yyyy/MM/dd", locale) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        colors = CardDefaults.defaultColors(
            color = if (isUnlocked) accentColor.copy(alpha = 0.12f)
            else MiuixTheme.colorScheme.surface.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Achievement badge; locked entries use a vector state icon.
            if (isUnlocked) {
                Text(
                    text = def.icon,
                    fontSize = 32.sp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "尚未解锁",
                    tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Name
            Text(
                text = def.name,
                fontSize = MiuixTheme.textStyles.body1.fontSize,
                fontWeight = FontWeight.SemiBold,
                color = if (isUnlocked) MiuixTheme.colorScheme.onSurface
                else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Description
            Text(
                text = def.description,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isUnlocked && unlockedDate != null) {
                // Unlocked date
                val dateStr = dateFormat.format(Date(unlockedDate))
                Text(
                    text = "已解锁 · $dateStr",
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    color = accentColor,
                    fontWeight = FontWeight.Medium
                )
            } else {
                // Progress bar
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = accentColor,
                        backgroundColor = accentColor.copy(alpha = 0.1f)
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }
        }
    }
}
