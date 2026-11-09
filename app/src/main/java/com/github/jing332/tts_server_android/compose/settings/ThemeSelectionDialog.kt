package com.github.jing332.tts_server_android.compose.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.jing332.tts_server_android.R
import com.github.jing332.tts_server_android.compose.theme.AppTheme
import com.github.jing332.tts_server_android.conf.AppConfig
import kotlinx.coroutines.delay

/**
 * 主题选择弹窗（11-10 十三改：AlertDialog → Dialog + Surface，显式控宽 92%）。
 *
 * ## 为什么不用 AlertDialog
 *
 * AlertDialog 宽度是「内容自适应 + 上限」：内容够宽就撑满，内容窄就跟着缩。
 * 旧版有 10 个主题 chip、内容需求超过屏宽，自然撑到接近满屏（用户印象里的"正常宽度"）；
 * 11-10 删到 5 个 chip 后内容需求降到 ~408dp，而手机可用宽仅 304~356dp，
 * 于是既撑不满、又装不下一行，换行成 3+2 —— 观感就是"弹窗变窄了"。
 *
 * 改 Dialog + Surface + fillMaxWidth(0.92f)：宽度与内容解耦，**不管几个 chip 都是同一宽度**，
 * 且 92% 屏宽（约 331dp @360dp 机型）足够一行放下 5 个 chip。
 *
 * ⚠️ 关掉平台默认宽度（usePlatformDefaultWidth = false）后必须自己给外边距，
 * 否则会贴屏边；这里靠 fillMaxWidth(0.92f) + 居中实现。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectionDialog(
    onDismissRequest: () -> Unit,
    currentTheme: AppTheme,
    onChangeTheme: (AppTheme) -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
                // 标题
                Text(
                    text = stringResource(id = R.string.theme),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                val view = LocalView.current
                val context = LocalContext.current
                var showWarn by remember { mutableStateOf(false) }
                AnimatedVisibility(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    visible = showWarn
                ) {
                    LaunchedEffect(showWarn) {
                        view.announceForAccessibility(context.getString(R.string.dynamic_color_not_support))
                        delay(2000)
                        showWarn = false
                    }

                    Text(
                        text = stringResource(id = R.string.dynamic_color_not_support),
                        modifier = Modifier.padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }

                // ===== 明暗（小标题独占一行 + FilterChip）=====
                Text(
                    stringResource(R.string.dark_mode_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        R.string.dark_mode_light to 1,
                        R.string.dark_mode_dark to 2,
                        R.string.dark_mode_follow to 0,
                    ).forEach { (resId, value) ->
                        val selected = AppConfig.darkModePref.value.coerceIn(0, 2) == value
                        FilterChip(
                            selected = selected,
                            // 与配色排同款：无 leadingIcon，选中态只靠 FilterChip 自带底色/描边
                            leadingIcon = null,
                            onClick = { AppConfig.darkModePref.value = value },
                            label = {
                                Text(
                                    stringResource(resId),
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        )
                    }
                }

                // ===== 配色（与上一组靠「小标题 + 间距」分隔）=====
                Text(
                    stringResource(R.string.color_scheme_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 11-08 用户令：存量选了已删主题的由 AppConfig 读取处兜底改选绿色。
                    // 「默认」档 10-12 已退役不列出。
                    AppTheme.values().filter { it != AppTheme.DEFAULT }.forEach {
                        val selected = currentTheme.id == it.id
                        FilterChip(
                            selected,
                            // 11-10 用户令「选中后的这个对勾也删掉」：撤 leadingIcon 对勾
                            leadingIcon = null,
                            onClick = {
                                if (it == AppTheme.DYNAMIC_COLOR && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                    showWarn = true
                                    return@FilterChip
                                }
                                onChangeTheme(it)
                            },
                            label = { Text(stringResource(id = it.stringResId), color = it.color) }
                        )
                    }
                }

                // 关闭（右下，与 AlertDialog 的按钮位一致）
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 14.dp),
                ) {
                    Text(stringResource(id = R.string.close))
                }
            }
        }
    }
}
