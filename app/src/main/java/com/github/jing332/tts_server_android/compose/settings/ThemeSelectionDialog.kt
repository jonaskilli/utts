package com.github.jing332.tts_server_android.compose.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.jing332.tts_server_android.R
import com.github.jing332.tts_server_android.compose.theme.AppTheme
import com.github.jing332.tts_server_android.conf.AppConfig
import kotlinx.coroutines.delay


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectionDialog(
    onDismissRequest: () -> Unit,
    currentTheme: AppTheme,
    onChangeTheme: (AppTheme) -> Unit
) {
    AlertDialog(onDismissRequest = onDismissRequest,
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
        title = {
            Text(text = stringResource(id = R.string.theme))
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(id = R.string.close))
            }
        }, text = {
            val view = LocalView.current
            val context = LocalContext.current
            Column {
                var showWarn by remember { androidx.compose.runtime.mutableStateOf(false) }
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
                        modifier = Modifier.padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }


                // ===== 明暗（11-10 七改，用户选 A 方案：小标题独占一行 + FilterChip）=====
                // 沿革：设置页三态胶囊（用户嫌丑）→ 挪进弹窗纯文字横排（用户嫌与配色分不清）
                // → 回 FilterChip，两组各用「独占一行的小标题 + chips」表达，
                // 区分靠**分组标题 + 间距**，不靠形状也不靠色点。
                Text(
                    stringResource(R.string.dark_mode_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
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

                // ===== 配色（11-10 七改，用户令「弹窗回 FilterChip」）=====
                // 色点+名字被否，回 FilterChip。与上一组靠「小标题 + 18dp 间距」分隔。
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
            }
        })
}
