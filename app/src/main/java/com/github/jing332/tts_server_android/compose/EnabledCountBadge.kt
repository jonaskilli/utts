package com.github.jing332.tts_server_android.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.jing332.tts_server_android.compose.theme.isAppInDarkTheme
import com.github.jing332.tts_server_android.compose.theme.AppTypeScale
import com.github.jing332.tts_server_android.R

/**
 * 启用计数徽章（10-13 用户令「主界面右上角菜单和设置页的朗读规则和插件也要后面显示
 * 已启用x，未启用两种显示方式」）：count>0 = 主题色「已启用 N」；count=0 = 红字「未启用」。
 * 朗读规则（全局最多启用一条）/ 插件（启用条数）两处用；密钥入口的 KeyPoolBadge 同族
 * （图一形式=紧贴标题，不落行尾）。未启用文案复用 role_entry_key_pool_empty。
 */
@Composable
fun EnabledCountBadge(enabledCount: Int, modifier: Modifier = Modifier) {
    val has = enabledCount > 0
    // 未启用红 = 日志失败条同款正宗红（10-13 晚用户令，与 KeyPoolBadge 同批同款）：
    // 浅色 #F44336 / 深色 #E57373；colorScheme.error 动态取色下偏暗紫红，弃用。
    // 11-10 修对比度：原 #F44336（深色 #E57373）压在自身 12% 红底上只有 2.98~3.01:1，
    // 配 10sp SemiBold 小字长期看是糊的（正文门槛 4.5）。换 M3 官方 error：
    // #BA1A1A 在八套主题的合成红底上实测 4.98~5.02:1，达标；深色档同步提亮。
    val emptyRed = if (isAppInDarkTheme()) Color(0xFFE88A96) else Color(0xFFBA1A1A)
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(
                (if (has) MaterialTheme.colorScheme.primary else emptyRed)
                    .copy(alpha = 0.12f)
            )
            .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (has) "已启用 $enabledCount" else stringResource(R.string.role_entry_key_pool_empty),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = AppTypeScale.badge),
            color = if (has) MaterialTheme.colorScheme.primary else emptyRed,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 自刷新的启用计数徽章（11-11 用户实报「外面显示未启用，进去勾选规则、退出来还是未启用，
 * 重启才正常」）。
 *
 * ## 根因
 *
 * 调用点写成 `val count = remember { dbm.speechRuleDao.allEnabled.size }`——`remember`
 * **没有 key**，整页只在该 Composable 首次组合时查一次库。之后从规则管理页返回，
 * 页面并没有被销毁重建（只是重新可见），所以那一枚计数永远是旧值；重启才重新组合。
 *
 * ## 做法
 *
 * 把「订阅 + 重查」收进本组件：ON_RESUME 时通过 [reloadKey] 自增触发重查。
 * 之所以不能只靠 `LaunchedEffect(Unit)`：它同样只在首次组合时跑一次，问题原样存在。
 *
 * 库层已开 `allowMainThreadQueries()`，`allEnabled` 是毫秒级同步查询，主线程重查可接受
 *（与调用点原有口径一致，不新增线程切换）。
 *
 * @param load 每次需要刷新时调用，返回当前启用条数
 */
@Composable
fun AutoEnabledCountBadge(
    load: () -> Int,
    modifier: Modifier = Modifier,
) {
    var reloadKey by remember { mutableIntStateOf(0) }
    // 首次 ON_RESUME 跳过：初次组合时下面的 remember(reloadKey) 已经查过了
    var firstResume by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (firstResume) firstResume = false else reloadKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // 用 rememberUpdatedState 存最新 lambda，把「查库」的触发条件收敛到 reloadKey：
    // 调用点写的是 `{ dbm...allEnabled.size }`，每次重组都是新实例，若直接当 remember 的 key
    // 就会每帧重查（虽然毫秒级，但没必要，也让"什么时候刷新"变得不可预期）。
    val latestLoad by rememberUpdatedState(load)
    val count = remember(reloadKey) { latestLoad() }
    EnabledCountBadge(count, modifier)
}
