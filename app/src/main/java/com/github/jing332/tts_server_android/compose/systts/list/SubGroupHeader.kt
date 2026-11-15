package com.github.jing332.tts_server_android.compose.systts.list

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Output
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.github.jing332.tts_server_android.compose.systts.ConfigDeleteDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import com.github.jing332.compose.widgets.AppDropdownMenu

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubGroupHeader(
    modifier: Modifier = Modifier,
    name: String,
    level: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
    toggleableState: ToggleableState = ToggleableState.Off,
    onToggleableStateChange: (Boolean) -> Unit = {},
    onRename: () -> Unit = {},
    onSort: () -> Unit = {},
    onBatchAssignTags: () -> Unit = {},
    hasTagKeyword: Boolean = false,
    onReassignTagsByGroupName: () -> Unit = {},
    onDelete: () -> Unit = {},
    onExport: () -> Unit = {},
    onDeleteEnabled: () -> Unit = {},
    onDeleteDisabled: () -> Unit = {},
    onExtractToGroup: () -> Unit = {},
    onMoveToOtherGroup: () -> Unit = {},
    onMoveEnabledToGroup: () -> Unit = {},
    itemCount: Int = -1,
    // 该子分组下是否还有下级子分组（与 itemCount>0 一起决定树图标显示）
    hasSubGroups: Boolean = false,
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        label = ""
    )

    // 按层级分配不同高度，让整体更协调。
    // 11-15 晚（用户选 A 档「行间距收紧」）：三档整体下移 2dp ——
    //   10/8 → **8/6**、8/6 → **6/4**、6/4 → **4/2**。
    // 为什么三档一起动、不是只改中间那档：只把 level1 压到 6/4 就与 level2 同高，
    // 层级台阶当场消失。整体下移 2 保住「每级差 2dp」的梯度。
    // 配 20dp 箭头后的实测行高（行高由文字行高决定，不是箭头）：
    //   子分组 level0 ≈ 38dp、level1 ≈ 34dp、level2 ≈ 30dp；
    //   一级分组行（GroupItem，箭头盒 36 + 4/4）= 44dp → 44 / 38 / 34 / 30 的台阶。
    val (paddingTop, paddingBottom) = when (level) {
        0 -> 8.dp to 6.dp
        1 -> 6.dp to 4.dp
        else -> 4.dp to 2.dp
    }

    var showOptions by remember { mutableStateOf(false) }
    var showExtraOptions by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog)
        ConfigDeleteDialog(
            onDismissRequest = { showDeleteDialog = false }, content = name, onConfirm = onDelete
        )

    Row(
        modifier = modifier
            .fillMaxWidth()
            // 11-08 终局（用户令「灰线没必要，撤掉」）：行底透明、无任何装饰线——
            // 层级只靠缩进+字号递减（Google 设置页子项同款「素」态）。
            // 沿革：surfaceContainer 深底条（10-13 B 档）→ primary 竖线（8bc9727）→
            // outlineVariant 灰线（d51106b）→ 全撤（本笔）。两级列表配参考线属过度装饰。
            // 配置项卡片（Item）、一级分组行（GroupItem）不动。
            .background(Color.Transparent)
            .clickable { if (!showOptions && !showExtraOptions) onClick() }
            .padding(
                // 11-15 晚终稿：start = **24dp**（缩进台阶制）。
                // 沿革：20（10-07 对齐一级箭头中心）→16（11-08「缩得太狠」）→
                // 28（箭头盒整格缩进）→32→34（对齐序号中心两轮）→24（本轮定稿）。
                //
                // 为什么从 34 退回 24：
                //   34 的来路是「子行箭头中心 = 父行序号中心（44）」，数学上对，但它把
                //   子分组头推到离**卡片左缘 8** 有 26dp，展开后是「标题缩进、内容不缩进」，
                //   看着左边空一块。用户 11-15 晚定调：**卡片左右边距一律不许动**（信息密度
                //   已经顶满），只准调一/二级分组头 → 中心对齐这条路只能放弃。
                //   改**台阶制**：缩进单位 16dp —— 一级/卡片 = 8，子分组头 = 8 + 16 = **24**；
                //   子名线 = 24 + 20(箭头) + 6 = **50**。
                //   左边距从此只有两条线（8 / 24），展开任何层级都不会出现「内容比标题靠左」
                //   的错觉——卡片 8 与一级头 8 同线，子分组只比它们多一级台阶。
                // 历史：28 那版是「正好缩进一个完整箭头盒」（一级 start 6 + 盒 28），
                // 16 时两级箭头只差 10dp、几乎看不出层级（用户实拍指出）。本轮台阶制
                // 取 16dp 缩进单位、落在 24，正好夹在 16 与 28 之间，既看得出台阶、
                // 又不会把子分组头推离卡片太远。
                // end 8（10-05 用户拍板：三行 ⋮ 统一 30dp 列）
                start = 24.dp,
                top = paddingTop,
                bottom = paddingBottom,
                end = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 11-08 回退细 chevron（用户装机实测「三角一般般」）。
        // 尺寸 24→**20dp**（11-15 晚用户令「子分组箭头小一号」）：
        // 这条**替换**了 11-08 那条「两级箭头大小一致」——当时是 30 vs 16（差一倍，
        // 看着像两个体系的图标），现在 24 vs 20 只差 4dp，而两者角色本就不同：
        // 父行箭头是「这一组能折叠」的操作入口，子行箭头是「层级台阶」的标记。
        // 更要紧的一条新依据：子行箭头现在与父行 **20dp 的序号徽章**同列同中心（44dp），
        // 同尺寸才像一列；24dp 箭头比徽章大一圈，会把序号压下去。
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = if (isExpanded) "收起" else "展开",
            modifier = Modifier
                .size(20.dp)
                .rotate(rotationAngle),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = name,
            // 特例许可（用户 2026-09-11 终裁）：回原版三档 17/16/16——层级差靠手调字号，token 档缩太多
            style = when (level) {
                0 -> MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp)
                1 -> MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp)
                else -> MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
            },
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                // 11-15 晚：start 3→**6dp**（配合 20dp 箭头，6dp 的视觉间隙 ≈8dp，
                // 与 10-05 那次「箭头离名字太远」的手感一致）。
                // 子名线 = 24(台阶) + 20(箭头) + 6 = **50**；一级名线 = 8+28+20+6 = 62。
                // 两级名字不再强行同线（那是「对齐序号中心」那版的做法，已放弃）：
                // 台阶制下名字线跟着台阶走，50 / 62 差 12dp 正好读出层级。
                .padding(start = 6.dp)
                .weight(1f)
        )

        // 含下级子分组（下方可展开）的子分组：行右侧树图标（15dp 主题色，与一级分组行一致）；直接是配置项的不带
        if (hasSubGroups) {
            Icon(
                imageVector = Icons.Default.AccountTree,
                contentDescription = "含下级内容",
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(15.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // 子分组内配置项数量
        // （行内魔棒快捷键已删：单击立即整理无确认，易误触把整组标签改掉；功能保留在右侧菜单「一键整理标签」）
        if (itemCount >= 0) {
            Text(
                "($itemCount)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp)
            )
        }

        TriStateCheckbox(
            state = toggleableState,
            onClick = {
                // 半选态单击直接全部取消（旧逻辑半选→先全选，会触发跨分组标签去重顶掉其他分组）
                    onToggleableStateChange(toggleableState == ToggleableState.Off)
            },
        )

        Box(
            modifier = Modifier
                .size(48.dp)
                .combinedClickable(
                    onClick = { showOptions = true },
                    onLongClick = { if (itemCount > 0) showExtraOptions = true }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "更多选项"
            )

            // 长按菜单：仅启用相关功能，仅组内非空时显示
            AppDropdownMenu(
                expanded = showExtraOptions,
                onDismissRequest = { showExtraOptions = false }
            ) {
                if (itemCount > 0) {
                    DropdownMenuItem(
                        text = { Text("删除启用的配置") },
                        onClick = {
                            showExtraOptions = false
                            onDeleteEnabled()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.DeleteForever, null)
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("删除未启用的配置") },
                        onClick = {
                            showExtraOptions = false
                            onDeleteDisabled()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.DeleteForever, null)
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("移动启用配置到其他分组") },
                        onClick = {
                            showExtraOptions = false
                            onMoveEnabledToGroup()
                        },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, null)
                        }
                    )
                }
            }

            // 主菜单：与启用状态无关的功能
            AppDropdownMenu(
                expanded = showOptions,
                onDismissRequest = { showOptions = false }
            ) {
                DropdownMenuItem(
                    text = { Text("重命名") },
                    onClick = {
                        showOptions = false
                        onRename()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.DriveFileRenameOutline, null)
                    }
                )

                if (itemCount > 0) DropdownMenuItem(
                    text = { Text("排序") },
                    onClick = {
                        showOptions = false
                        onSort()
                    },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Default.Sort, null)
                    }
                )

                // 一键整理标签：子分组名含关键词且组内非空时显示
                if (hasTagKeyword && itemCount > 0) {
                    DropdownMenuItem(
                        text = { Text("一键整理标签") },
                        onClick = {
                            showOptions = false
                            onReassignTagsByGroupName()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.AutoFixHigh, null)
                        }
                    )
                }

                DropdownMenuItem(
                    text = { Text("转为一级分组") },
                    onClick = {
                        showOptions = false
                        onExtractToGroup()
                    },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
                    }
                )

                DropdownMenuItem(
                    text = { Text("移动到其他一级分组") },
                    onClick = {
                        showOptions = false
                        onMoveToOtherGroup()
                    },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.DriveFileMove, null)
                    }
                )

                if (itemCount > 0) DropdownMenuItem(
                    text = { Text("导出") },
                    onClick = {
                        showOptions = false
                        onExport()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Output, null)
                    }
                )

                HorizontalDivider()

                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showOptions = false
                        showDeleteDialog = true
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
}
