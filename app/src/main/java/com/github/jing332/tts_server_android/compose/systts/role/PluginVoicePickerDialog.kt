package com.github.jing332.tts_server_android.compose.systts.role

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.jing332.database.dbm
import com.github.jing332.tts.speech.plugin.engine.TtsPluginUiEngineV2

/**
 * 插件音色选择（11-17，Fish 按书配音支线）。
 *
 * 背景：这条支线的角色音色存在**插件自己的音色表**里（Fish 7969 个），不是 App 的
 * 「发音人配置项」，所以默认换声面板的候选（= 启用配置的 tag）看不到它们。
 * 这里直接问插件要音色列表（`EditorJS.getVoices`），选中的 id + 名字写回
 * `characterRecords.json` 的 `voice` / `voiceName`，让角色列表照常回显与朗读。
 *
 * 只在「该角色当前绑定的标签对应一条插件配置」时由角色列表菜单进入。
 */
@Composable
fun PluginVoicePickerDialog(
    context: Context,
    pluginId: String,
    locale: String,
    roleName: String,
    currentVoiceId: String,
    onPick: (id: String, name: String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    var voices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pluginId, locale) {
        loading = true
        error = null
        runCatching {
            val plugin = dbm.pluginDao.getByPluginId(pluginId)
                ?: throw IllegalStateException("插件不存在：$pluginId")
            val engine = TtsPluginUiEngineV2(context, plugin)
            engine.eval()
            engine.onLoadData()
            val map = engine.getVoices(locale.ifBlank { "zh" })
            voices = map.map { it.id to (it.name.ifBlank { it.id }) }
        }.onFailure { error = it.message ?: it.toString() }
        loading = false
    }

    val shown = remember(voices, keyword) {
        val k = keyword.trim()
        if (k.isEmpty()) voices else voices.filter { it.second.contains(k, true) || it.first.contains(k, true) }
    }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        onDismissRequest = onDismissRequest,
        title = { Text("插件音色 · $roleName") },
        text = {
            Column {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    singleLine = true,
                    label = { Text("搜索音色名或 ID") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
                when {
                    loading -> Text("正在读取插件音色…")
                    error != null -> Text("读取失败：$error")
                    shown.isEmpty() -> Text("没有匹配的音色")
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                        items(shown, key = { it.first }) { (id, name) ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(id, name) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                            ) {
                                Text(
                                    text = name + if (id == currentVoiceId) "  ✓" else "",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (id == currentVoiceId) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = id,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("取消") }
        },
    )
}