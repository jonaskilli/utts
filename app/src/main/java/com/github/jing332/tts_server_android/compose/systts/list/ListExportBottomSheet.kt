package com.github.jing332.tts_server_android.compose.systts.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jing332.tts_server_android.compose.systts.ConfigExportBottomSheet
import com.github.jing332.tts_server_android.constant.AppConst
import com.github.jing332.database.entities.systts.GroupWithSystemTts
import com.github.jing332.database.entities.systts.TtsConfigurationDTO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

/**
 * 导出前抹掉 tagRuleId（用户令 11-11）：配置列表的 JSON 直接和 z阅读 通用，对方多一个字段看不懂、
 * 自己看也碍眼。
 *
 * 为什么不删字段、只抹值：tagRuleId 在库里承担「角色数据目录 / 密钥池 / 插件回调身份」三件事，
 * 从实体删掉要升 Room 版本 + 写迁移，风险不成比例。而导出抹成空串后：
 *  - 序列化：AppConst.jsonBuilder 默认 encodeDefaults=false，空串等于默认值 → 该键整行消失，
 *    JSON 与 z阅读 侧字段完全对齐；
 *  - 回导：两条通路都会自动补回来——原生格式走 migrateTagNamesIfNeed（tag 非空 + tagRuleId 空
 *    → 取当前启用规则），z阅读 格式走 insertJReadItems（同口径）。朗读规则全局只允许启用一条，
 *    所以「不写 = 用当前那条」没有歧义。
 *  - 兜底：万一没补上，TextProcessor 运行期也会空值回退到启用规则，不会再 MissingRule。
 *
 * 只用在「配置列表导出」。整机备份（Migration.kt 的导出、backup 打包）保持原样——那是完整快照，
 * 抹字段会让旧规则绑定信息凭空丢失。
 */
internal fun List<GroupWithSystemTts>.stripTagRuleIdForExport(): List<GroupWithSystemTts> =
    map { group ->
        group.copy(
            list = group.list.map { tts ->
                val cfg = tts.config as? TtsConfigurationDTO
                if (cfg == null || cfg.speechRule.tagRuleId.isEmpty()) tts
                else tts.copy(config = cfg.copy(speechRule = cfg.speechRule.copy(tagRuleId = "")))
            }
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListExportBottomSheet(onDismissRequest: () -> Unit, list: List<GroupWithSystemTts>) {
    // 第9项: 将大 JSON 序列化移到 IO 线程, 避免主线程阻塞导致导出页卡顿/慢。
    // 修复: 导出文件用 prettyPrint (jsonBuilder), 每个配置项独立一行可读。
    //   速度仍快——序列化在 IO 线程, prettyPrint 开销可忽略。
    var json by remember(list) { mutableStateOf<String?>(null) }
    LaunchedEffect(list) {
        json = withContext(Dispatchers.IO) {
            // 导出前抹掉 tagRuleId：与 z阅读 配置列表字段对齐（见 stripTagRuleIdForExport 注释）
            AppConst.jsonBuilder.encodeToString(list.stripTagRuleIdForExport())
        }
    }
    val jStr = json
    if (jStr == null) {
        // 加载状态放进 ModalBottomSheet 内, 避免全屏灰色遮罩
        ModalBottomSheet(onDismissRequest = onDismissRequest) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .wrapContentSize(Alignment.Center)
            ) {
                CircularProgressIndicator()
            }
        }
    } else {
        val fileName = when {
            list.size == 1 && list[0].list.size == 1 -> {
                val cfgName = list[0].list[0].displayName
                "配置列表-${cfgName}.json"
            }
            list.size == 1 -> {
                val groupName = list[0].group.name
                val count = list[0].list.size
                "配置列表-${groupName}-${count}项.json"
            }
            else -> {
                val groupCount = list.size
                val totalCount = list.sumOf { it.list.size }
                "配置列表-${groupCount}个分组-${totalCount}项.json"
            }
        }
        ConfigExportBottomSheet(
            json = jStr,
            onDismissRequest = onDismissRequest,
            fileName = fileName
        )
    }
}
