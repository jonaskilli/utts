package com.github.jing332.tts_server_android.compose.systts.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Output
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import com.github.jing332.common.utils.StringUtils.limitLength
import com.github.jing332.common.utils.limitDisplayLength
import com.github.jing332.common.utils.limitByDisplayWidth
import com.github.jing332.tts_server_android.compose.theme.pluginChipColor
import com.github.jing332.common.utils.performLongPress
import com.github.jing332.compose.widgets.AppDropdownMenu
import com.github.jing332.compose.widgets.LongClickIconButton
import com.github.jing332.compose.widgets.htmlcompose.HtmlText
import com.github.jing332.tts_server_android.R
import com.github.jing332.tts_server_android.compose.theme.AppShapes
import com.github.jing332.tts_server_android.compose.theme.neutralCardColor
import com.github.jing332.tts_server_android.conf.AppConfig
import com.github.jing332.tts_server_android.service.systts.help.VoiceMarksFile
import org.burnoutcrew.reorderable.ReorderableLazyListState
import org.burnoutcrew.reorderable.detectReorder


@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Item(
    modifier: Modifier,
    name: String,
    // 点亮的发音人标记 emoji（用户 09-12 晚：与日志面板同源 voice_marks.json，跟在名字后显示）
    marks: List<String> = emptyList(),
    // 标记 toggle（10-06 用户令：卡片⋮菜单也可打标记）；默认空实现=旧调用方零改动
    onToggleMark: (String) -> Unit = {},
    tagName: String,
    type: String,
    desc: String,
    params: String,
    reorderState: ReorderableLazyListState,

    standby: Boolean,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,

    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onAudition: () -> Unit,
    onExport: () -> Unit,
    onMoveToSubGroup: () -> Unit = {},
    onSwitchTag: () -> Unit = {},
    // 音频参数：卡片⋮菜单直达三层弹窗（配置项/插件/全局），不进编辑页（用户要求就地触发）
    onAudioParams: () -> Unit = {},
    // BGM 走独立字段(BgmConfiguration.volume)，不参与 audioParams 三层体系，
    // 弹窗对它无意义(AudioParamsDialog 对非 TtsConfigurationDTO 直接 return)，故菜单隐藏该项
    showAudioParamsEntry: Boolean = true,
    isInSubGroup: Boolean = false,
) {
    val view = LocalView.current
    val context = LocalContext.current

    val limitNameLen by remember { AppConfig.limitNameLength }
    // 统一截断（10-06）：码点安全+竖线段回退，与日志/弹窗一口径；
    // 标记 emoji 拼在截断后名字后（同槽一起限长的口径见下 nameWithMarks）
    val limitedName = remember(name, limitNameLen) {
        if (limitNameLen == 0) name else name.limitDisplayLength(limitNameLen)
    }
    // 点亮的标记 emoji 拼在名字后（未点亮不占位；顺序固定 喜欢→路人→坏人，见 VoiceMarksFile.MARK_ITEMS）。
    // 名字超长被省略号吃掉时标记可能一并裁掉——与日志面板同取舍：名字优先，不额外占一行
    val markEmoji = VoiceMarksFile.emojiOf(marks)
    val nameWithMarks = if (markEmoji.isEmpty()) limitedName else "$limitedName $markEmoji"

    // 11-11 三次修订（用户两轮反馈合并口径）：中性灰 #EEEEEE / 深色 #262626。
    // 沿革：①hunyuan 时代吃 M3 默认色（surfaceContainerLow = #F7F2FA，微紫，C*=4.49）
    // → ②10-10「底色中性化」批把中性槽压成纯灰 #F9F9F9（C*=0.01），卡片与页底对比只剩 1.028
    // → ③11-08 试过纯白+6dp 阴影，装机实测「不如以前」回滚
    // → ④11-11 早先按用户令回 #F7F2FA，但用户随即反馈「某些主题下太突兀」（微紫在绿/雾底上串色），
    //    而退回旧灰 #F9F9F9 又「不够突出」（对紫页底只有 1.004）→ ⑤本档。
    // 判据见 theme/Theme.kt 的 neutralCardColor()（彩度 0 + 四主题对比 1.107~1.133）。
    // ⚠️ 只改颜色、不加阴影——11-08 翻车的是 6dp 重阴影，不是颜色。
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = neutralCardColor()
        )
    ) {
        // 卡片中间区域单击：与右侧显式按钮互补
        // 默认(swapButton=false,右侧是编辑)：单击=试听；交换后(swapButton=true,右侧是试听)：单击=编辑
        val swapButton = AppConfig.isSwapListenAndEditButton.value
        ConstraintLayout(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .combinedClickable(
                    onClickLabel = stringResource(if (swapButton) R.string.edit else R.string.audition),
                    onClick = { if (swapButton) onEdit() else onAudition() },
                )
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(context.getString(R.string.edit)) { onEdit(); true },
                        CustomAccessibilityAction(context.getString(R.string.delete)) { onDelete(); true },
                        CustomAccessibilityAction(context.getString(R.string.copy)) { onCopy(); true },
                        CustomAccessibilityAction(context.getString(R.string.audition)) { onAudition(); true },
                        CustomAccessibilityAction(context.getString(R.string.export_config)) { onExport(); true }
                    )
                }
                .padding(vertical = 4.dp)
        ) {
            val (
                checkRef,
                nameRef,
                contentRef,
                targetRef,
                typeRef,
                buttonsRef,
            ) = createRefs()
            Row(
                Modifier
                    .constrainAs(checkRef) {
                        start.linkTo(parent.start)
                        top.linkTo(nameRef.top)
                        bottom.linkTo(contentRef.bottom)

                        height = Dimension.fillToConstraints
                    }
                    .detectReorder(reorderState)) {
                // 用户 09-12：启用开关统一用方框（原来子分组里是圆圈当开关用，与分组行/一级项不一致）
                Checkbox(
                    modifier = Modifier
                        .fillMaxHeight()
                        .semantics {
                            role = Role.Switch
                            context
                                .getString(
                                    if (enabled) R.string.config_enabled_desc else R.string.config_disabled_desc,
                                    limitedName
                                )
                                .let {
                                    contentDescription = it
                                    stateDescription = it
                                }
                        },
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
            Text(
                nameWithMarks,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Start,
                // 省略号截断（原 Clip 硬裁）；右边界收到标签左侧，长标题不再穿到标签底下
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .constrainAs(nameRef) {
                        start.linkTo(checkRef.end)
                        end.linkTo(targetRef.start)
                        top.linkTo(parent.top)
                        width = Dimension.fillToConstraints
                    }
                    .padding(bottom = 4.dp)
            )

            Column(
                Modifier
                    .constrainAs(contentRef) {
                        start.linkTo(checkRef.end)
                        top.linkTo(nameRef.bottom)
                        bottom.linkTo(parent.bottom)
                    }
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                HtmlText(
                    text = desc,
                    // <small> 层标靠 RelativeSizeSpan 缩放，换算基准是 fontSize 参数；
                    // 不传(默认 Unspecified)则相对缩放被静默丢弃、层标缩不了——必须显式传正文字号。
                    // 特例许可（用户 2026-09-11 终裁）：回原版 13sp，与参数行 bodySmall 12sp 拉开一档
                    fontSize = 13.sp,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    ),
                )

                HtmlText(
                    text = params,
                    // 11-10 十一改（用户令「就参照以前 hunyuan 分支的来改」）：
                    // onSurfaceVariant → onBackground，与上方 desc 同级。
                    // 混元分支的 desc 与 params 都是 onBackground——两者同属「卡的正文信息」，
                    // 不该分两档灰。此前 params 被压成 onSurfaceVariant（中灰），
                    // 造成同一张卡里正文两种深浅。
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onBackground),
                )
            }

            val limitLen by remember { AppConfig.limitTagLength }
            val limitedTagName = remember(tagName, limitLen) {
                if (limitLen == 0) tagName else tagName.limitLength(limitLen)
            }
            // 标签锚点常驻（无标签时 Box 为 0 宽），保证标题 end.linkTo(targetRef.start) 约束稳定
            Box(
                Modifier
                    .constrainAs(targetRef) {
                        top.linkTo(nameRef.top)
                        end.linkTo(parent.end)
                    }
                    .padding(end = 4.dp)
            ) {
                if (limitedTagName.isNotEmpty())
                    TagScreen(
                        Modifier.clickable { onSwitchTag() },
                        tag = limitedTagName,
                    )
            }

            Row(modifier = Modifier.constrainAs(buttonsRef) {
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                end.linkTo(parent.end)
            }) {
                val swapButton = AppConfig.isSwapListenAndEditButton.value
                IconButton(
                    modifier = Modifier,
                    onClick = { if (swapButton) onAudition() else onEdit() }
                ) {
                    if (swapButton)
                        Icon(Icons.Default.Headphones, stringResource(id = R.string.audition))
                    else
                        Icon(Icons.Default.Edit, stringResource(id = R.string.edit_desc, name))
                }

                var showOptions by remember { mutableStateOf(false) }
                LongClickIconButton(
                    onClick = { showOptions = true },
                    onLongClick = { if (swapButton) onEdit() else onAudition() },
                    onLongClickLabel = stringResource(id = if (swapButton) R.string.edit else R.string.audition)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        stringResource(id = R.string.more_options_desc, name)
                    )

                    AppDropdownMenu(
                        expanded = showOptions,
                        onDismissRequest = { showOptions = false }) {

                        DropdownMenuItem(
                            text = { Text(stringResource(id = if (swapButton) R.string.edit else R.string.audition)) },
                            onClick = {
                                showOptions = false
                                if (swapButton)
                                    onEdit()
                                else
                                    onAudition()
                            },
                            leadingIcon = {
                                Icon(
                                    if (swapButton) Icons.Default.Edit else Icons.Default.Headphones,
                                    null
                                )
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.copy)) },
                            onClick = {
                                showOptions = false
                                onCopy()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.CopyAll, stringResource(R.string.copy))
                            }
                        )
                        // 音频参数：直达三层弹窗（配置项/插件/全局），不进编辑页
                        if (showAudioParamsEntry)
                            DropdownMenuItem(
                                text = { Text(stringResource(id = R.string.audio_params)) },
                                onClick = {
                                    showOptions = false
                                    onAudioParams()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Speed, stringResource(R.string.audio_params))
                                }
                            )
                        DropdownMenuItem(
                            // 「其他分组」统称：选择器支持 同组换子分组/跨大分组/新建/回根目录，
                            // 原名「移动到子分组」漏了跨大分组与根目录（用户 09-12 拍板改名）
                            text = { Text("移动到其他分组") },
                            onClick = {
                                showOptions = false
                                onMoveToSubGroup()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.AccountTree, null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.export_config)) },
                            onClick = {
                                showOptions = false
                                onExport()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Output, stringResource(R.string.export_config))
                            }
                        )
                        // 发音人标记（10-06 用户令：卡片⋮也可打标记，与换声弹窗行内 ⋮ 同源同款）：
                        // 多选 toggle、点一次切一次菜单不关（可连点几个），点亮态即时反映到名字后 emoji
                        VoiceMarksFile.MARK_ITEMS.forEach { (mark, emojiText, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = { Text(emojiText) },
                                trailingIcon = {
                                    if (mark in marks)
                                        Icon(Icons.Default.Check, stringResource(R.string.tagged_mark_on))
                                },
                                onClick = { onToggleMark(mark) },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.delete)) },
                            onClick = {
                                showOptions = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DeleteForever,
                                    null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .constrainAs(typeRef) {
                        end.linkTo(parent.end)
//                        top.linkTo(buttonsRef.bottom)
                        bottom.linkTo(parent.bottom)
                    }
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (standby) {
                    Text(
                        modifier = Modifier.padding(end = 4.dp),
                        text = stringResource(id = R.string.systts_standby),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    // 11-10 用户令：插件名遇符号即切（剪映_v12→剪映、讯飞（Pro）→讯飞），
                    // 与角色列表/换声面板/日志统一走 limitByDisplayWidth，不再各自一套口径
                    text = type.limitByDisplayWidth(),
                    style = MaterialTheme.typography.labelMedium,
                    // 11-10 用户令「参考日志里插件名的颜色」：改用日志插件牌的固定灰青，
                    // 原 tertiary 会随主题变（青蓝/绿/紫），与"来源标识"语义不符
                    color = pluginChipColor(),
                    // 右侧留 8dp 空隙避免与左侧采样率标签贴死
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }

        }

    }
}

@Composable
private fun TagScreen(modifier: Modifier = Modifier, tag: String) {
    // 11-10 六改（用户令「标签全部回 8dp 方框」）：原用 MaterialTheme.shapes.small，
    // 聚合后是 AppShapes.sm = 6dp，与角色列表标签（AppShapes.md = 8dp）差 2dp——
    // 两个页面同属"分类标签"却不同形状。现统一到 AppShapes.md。
    // Medium 字重与旁边 Bold 的显示名拉开层级（口径不变）。
    OutlinedCard(shape = AppShapes.md, modifier = modifier) {
        Text(
            text = tag,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
