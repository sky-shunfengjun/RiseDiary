package com.risediary.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.risediary.app.ui.icons.AppIcons
import com.risediary.app.ui.theme.RiseCard
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DataReadError(retry: () -> Unit, message: String = "记录读取失败，已有内容已保留") {
    RiseCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(message,color=MiuixTheme.colorScheme.error)
            Button(onClick=retry) { Icon(AppIcons.Refresh,null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("重试") }
        }
    }
}
