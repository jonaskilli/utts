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
        // 11-15 晚：24→22dp（微信/QQ 同款体量）。底栏只有 60dp 内容高，
        // 图标小一号、标签 10sp，整条才「宽松」得起来（用户对比实拍）。
        Icon(
            modifier = Modifier.size(22.dp),
            painter = painterResource(id = R.drawable.ic_config),
            contentDescription = null
        )
    })

    // 角色管理：独立于发音人，承载「仅界面模式」的工具型插件
    object Tool : PagerDestination(2, R.string.role_management, R.string.role_management, {
        Icon(
            modifier = Modifier.size(22.dp),
            imageVector = Icons.Outlined.AccountCircle,
            contentDescription = null
        )
    })

    object SystemTtsLog : PagerDestination(1, R.string.log, R.string.log, {
        Icon(
            Icons.AutoMirrored.Outlined.TextSnippet,
            contentDescription = null
        )
    })

    object Settings : PagerDestination(3, R.string.settings, R.string.settings, {
        Icon(Icons.Outlined.Settings, contentDescription = null)
    })
}