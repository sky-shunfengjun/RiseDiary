package com.risediary.app.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.risediary.app.R
import com.risediary.app.ui.theme.LocalRiseDarkTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun WelcomeOnboardingPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        InstrumentHero()
        Spacer(modifier = Modifier.height(30.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            fontSize = MiuixTheme.textStyles.title1.fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MiuixTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_subtitle),
            fontSize = MiuixTheme.textStyles.headline1.fontSize,
            textAlign = TextAlign.Center,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(14.dp),
            color = MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = stringResource(R.string.onboarding_usage_disclaimer_title),
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.usage_disclaimer),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WelcomeStatus(Icons.Default.Storage, stringResource(R.string.onboarding_status_local), Modifier.weight(1f))
            WelcomeStatus(Icons.Default.CloudOff, stringResource(R.string.onboarding_status_no_account), Modifier.weight(1f))
            WelcomeStatus(Icons.Default.Tune, stringResource(R.string.onboarding_status_editable), Modifier.weight(1f))
        }
    }
}

@Composable
private fun InstrumentHero() {
    val dark = LocalRiseDarkTheme.current
    val accent = if (dark) Color(0xFF59C3FF) else Color(0xFF087FCC)
    Box(modifier = Modifier.size(176.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(accent.copy(alpha = 0.08f))
            drawCircle(accent.copy(alpha = 0.28f), size.minDimension * 0.42f, style = Stroke(2.dp.toPx()))
            drawCircle(accent.copy(alpha = 0.16f), size.minDimension * 0.29f, style = Stroke(1.dp.toPx()))
            repeat(12) { index ->
                val angle = Math.toRadians(index * 30.0)
                val outer = size.minDimension * 0.48f
                val inner = size.minDimension * 0.44f
                val center = Offset(size.width / 2f, size.height / 2f)
                drawLine(
                    accent.copy(alpha = 0.28f),
                    Offset(center.x + kotlin.math.cos(angle).toFloat() * inner, center.y + kotlin.math.sin(angle).toFloat() * inner),
                    Offset(center.x + kotlin.math.cos(angle).toFloat() * outer, center.y + kotlin.math.sin(angle).toFloat() * outer),
                    1.dp.toPx()
                )
            }
        }
        Image(
            painterResource(R.drawable.app_icon),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(92.dp).clip(RoundedCornerShape(28.dp))
        )
    }
}

@Composable
private fun WelcomeStatus(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Surface(modifier, RoundedCornerShape(16.dp), color = MiuixTheme.colorScheme.surface.copy(alpha = 0.72f)) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = MiuixTheme.colorScheme.primary)
            Text(
                text,
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                textAlign = TextAlign.Center,
                color = MiuixTheme.colorScheme.onSurface
            )
        }
    }
}
