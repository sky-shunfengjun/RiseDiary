package com.risediary.app.ui.achievement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
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
                .padding(innerPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
    accentColor: androidx.compose.ui.graphics.Color
) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { SimpleDateFormat("yyyy/MM/dd", locale) }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) accentColor.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.08f)
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
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Name
            Text(
                text = def.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isUnlocked) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Description
            Text(
                text = def.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isUnlocked && unlockedDate != null) {
                // Unlocked date
                val dateStr = dateFormat.format(Date(unlockedDate))
                Text(
                    text = "已解锁 · $dateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Medium
                )
            } else {
                // Progress bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = accentColor,
                    trackColor = accentColor.copy(alpha = 0.1f),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }
        }
    }
}
