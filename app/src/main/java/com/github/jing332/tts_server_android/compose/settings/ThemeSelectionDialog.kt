package com.github.jing332.tts_server_android.compose.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
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


                // ===== 明暗（11-10 立，用户令：从设置页挪进来，不要胶囊、不要示意图）=====
                // 形态与下方「配色」完全同构：左侧小标签 + 右侧横排选项、无底色、
                // 选中靠主题色 + 加粗。两排的区分**不靠形状**，靠"有没有色点"——
                // 明暗与具体颜色无关故无点，配色本身就是颜色故有点。
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        stringResource(R.string.dark_mode_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(34.dp)
                    )
                    Spacer(Modifier.width(18.dp))
                    listOf(
                        stringResource(R.string.dark_mode_light) to 1,
                        stringResource(R.string.dark_mode_dark) to 2,
                        stringResource(R.string.dark_mode_follow) to 0,
                    ).forEach { (label, value) ->
                        val selected = AppConfig.darkModePref.value.coerceIn(0, 2) == value
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { AppConfig.darkModePref.value = value }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }

                // ===== 配色（11-10 改：FilterChip → 色点 + 名字）=====
                // 原 FilterChip 带边框底色，与上方纯文字的「明暗」排是两套视觉语言
                // （用户实问「怎么跟下面的主题区分」）。改成无底色横排后两排同构，
                // 选中态=色点外圈同色环 + 文字变主题色加粗（照 DSH-Go 的色点做法）。
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        stringResource(R.string.color_scheme_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(34.dp).padding(top = 4.dp)
                    )
                    Spacer(Modifier.width(18.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AppTheme.values().filter { it != AppTheme.DEFAULT }.forEach {
                            val selected = currentTheme.id == it.id
                            // 动态颜色没有固定色相，用中性灰点占位（它的色点由壁纸决定）
                            val dotColor = if (it == AppTheme.DYNAMIC_COLOR)
                                MaterialTheme.colorScheme.outline else it.color
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        if (it == AppTheme.DYNAMIC_COLOR && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                            showWarn = true
                                            return@clickable
                                        }
                                        onChangeTheme(it)
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(15.dp)
                                        // 选中：外圈一道同色环（2dp 间隙），不用对勾不用底块
                                        .then(
                                            if (selected)
                                                Modifier.border(2.dp, dotColor, CircleShape).padding(3.dp)
                                            else Modifier.padding(1.dp)
                                        )
                                        .background(dotColor, CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(id = it.stringResId),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        })
}
