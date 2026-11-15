package com.github.jing332.tts_server_android.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.jing332.tts_server_android.R

sealed class PagerDestination(
    val index: Int,
    @StringRes val strId: Int,
    @StringRes val contentDescId: Int,
    val icon: @Composable () -> Unit = {},
) {
    companion object {
        val routes by lazy {
            listOf(
                SystemTts,
                SystemTtsLog,
                Tool,
                Settings
            )
        }
    }

    object SystemTts : PagerDestination(0, R.string.system_tts, R.string.system_tts, {
        // 11-16：22→**26dp**。用户拿 QQ 实拍对比「图标和字都比咱们的大」——
        // 上一版按微信那档把图标压到 22dp 是压过头了；QQ 是 26dp 档。
        // 60dp 内容高里 26 + 2 + 16 行高 = 44dp，上下各 8dp，仍宽松。
        Icon(
            modifier = Modifier.size(26.dp),
            painter = painterResource(id = R.drawable.ic_config),
            contentDescription = null
        )
    })

    // 角色管理：独立于发音人，承载「仅界面模式」的工具型插件
    object Tool : PagerDestination(2, R.string.role_management, R.string.role_management, {
        Icon(
            modifier = Modifier.size(26.dp),
            imageVector = Icons.Outlined.AccountCircle,
            contentDescription = null
        )
    })

    object SystemTtsLog : PagerDestination(1, R.string.log, R.string.log, {
        Icon(
            Icons.AutoMirrored.Outlined.TextSnippet,
            contentDescription = null,
            // 11-16：四个图标统一 26dp（之前这两个没显式尺寸、走默认 24dp，
            // 与另外两枚差 2dp——用户最容易看出这种不一致）
            modifier = Modifier.size(26.dp)
        )
    })

    object Settings : PagerDestination(3, R.string.settings, R.string.settings, {
        Icon(
            Icons.Outlined.Settings,
            contentDescription = null,
            modifier = Modifier.size(26.dp)
        )
    })
}