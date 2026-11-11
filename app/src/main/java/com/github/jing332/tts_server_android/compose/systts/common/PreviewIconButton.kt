package com.github.jing332.tts_server_android.compose.systts.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 试听键（播放 / 合成中 / 停止）——11-11 用户令「播放键跟三个点菜单要同级，
 * 不应该一大一小，播放键要大点」。
 *
 * ## 为什么从字符改成图标
 * 角色列表与换声面板原来用字符 ▶ / … / ■ 渲染三态，而 ⋮ 是 Material 图标。
 * 实测字符 ▶ 在手机系统字体里的字形只有 **0.59em 高**（NotoSansSymbols 回落），
 * 16sp 时视觉仅 ≈ 9.4dp；⋮ 图标却是 14/20dp —— 这就是「一大一小」的来源。
 * 想让字符追平 20dp，字号要推到约 34sp，会把整行撑高，也随字体缩放漂移。
 * 改图标后：视觉尺寸由 [iconSize] 直接决定，与 ⋮ 完全同规格、不受字体影响；
 * 三态对应 PlayArrow / MoreHoriz / Stop，颜色仍由调用方给（空闲=主色、合成=琥珀、播放=红）。
 *
 * ## 为什么不用 IconButton
 * M3 IconButton 会按 LocalMinimumInteractiveComponentSize 叠到 48dp，把行高撑开
 * （角色列表为此反复缩过 ⋮ 盒，见 VoiceOverflowMenu 注释）。这里直接 Box + clickable，
 * 视觉盒 = 触控盒 = [boxSize]（与同行的 ⋮ 同款取舍：右侧紧邻整行可点区域）。
 */
@Composable
internal fun PreviewIconButton(
    playing: Boolean,
    synthesizing: Boolean,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    boxSize: Dp = 24.dp,
    iconSize: Dp = 20.dp,
    contentDescription: String = "试听",
) {
    val icon = when {
        synthesizing -> Icons.Filled.MoreHoriz
        playing -> Icons.Filled.Stop
        else -> Icons.Filled.PlayArrow
    }
    Box(
        modifier = modifier
            .size(boxSize)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}
