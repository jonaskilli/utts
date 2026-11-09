package com.github.jing332.tts_server_android.compose.codeeditor

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.jing332.common.LogLevel
import com.github.jing332.script.runtime.console.Console
import com.github.jing332.script.runtime.console.LogListener
import com.github.jing332.script.runtime.console.LogListenerManager
import com.github.jing332.tts_server_android.compose.theme.AppTheme
import com.github.jing332.tts_server_android.compose.theme.logLevelColor
import com.github.jing332.tts_server_android.compose.theme.neutralSwitchUncheckedTrackColor


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoggerBottomSheet(
    registry: LogListenerManager,
    darkTheme: Boolean = isSystemInDarkTheme(),
    onDismissRequest: () -> Unit,
    onLaunched: () -> Unit,
) {
    var logText by remember { mutableStateOf(AnnotatedString("")) }


    // 11-10 主题化：同 LoggerFloatingScreen——颜色先在 Composition 里算好再被回调捕获
    // ⚠️ logLevelColor() 是 @Composable，不能直接在 remember {} 里调
    //（remember 的 calculation 是 @DisallowComposableCalls）——先在 Composition 里求值，
    // 再把结果当 remember 的 key。切换主题/深浅时这六个颜色变 → map 重建 → listener 换新。
    val infoColor = logLevelColor(LogLevel.INFO)
    val debugColor = logLevelColor(LogLevel.DEBUG)
    val traceColor = logLevelColor(LogLevel.TRACE)
    val warnColor = logLevelColor(LogLevel.WARN)
    val errorColor = logLevelColor(LogLevel.ERROR)
    val successColor = logLevelColor(LogLevel.SUCCESS)
    val levelColors = remember(infoColor, debugColor, traceColor, warnColor, errorColor, successColor) {
        mapOf(
            LogLevel.INFO to infoColor,
            LogLevel.DEBUG to debugColor,
            LogLevel.TRACE to traceColor,
            LogLevel.WARN to warnColor,
            LogLevel.ERROR to errorColor,
            LogLevel.SUCCESS to successColor,
        )
    }

    val listener = remember(levelColors) {
        LogListener { entry ->
            logText = buildAnnotatedString {
                append(logText)

                withStyle(SpanStyle(color = levelColors[entry.level] ?: levelColors.getValue(LogLevel.INFO))) {
                    appendLine(entry.message)
                }
            }
        }
    }

    LaunchedEffect(registry) {
        registry.addLogListener(listener)
        onLaunched()
    }

    DisposableEffect(registry) {
        onDispose {
            registry.removeLogListener(listener)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        SelectionContainer(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                logText, modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 8.dp)
            )
        }
    }
}

@Preview
@Composable
fun LoggerBottomSheetPreview() = AppTheme {
    var show by remember { mutableStateOf(true) }
    if (show) {
        val logger = remember { Console() }
        LoggerBottomSheet(
            registry = logger,
            darkTheme = false,
            onDismissRequest = { show = false },
            onLaunched = {
//                logger.verbose("Hello, I am verbose.")
//                logger.debug("Hello, I am debug.")
//                logger.info("Hello, I am info.")
//                logger.warn("Hello, I am warn.")
//                logger.error("Hello, I am error.")
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        var darkMode by remember { mutableStateOf(false) }
        Column(Modifier.align(Alignment.Center)) {
            Text("Dark theme")
            Switch(
                darkMode,
                {
                    darkMode = it
                    show = true
                },
                // 未选中轨道中性化（10-13 深夜用户令「未开启的不应该有颜色」）
                colors = SwitchDefaults.colors(
                    uncheckedTrackColor = neutralSwitchUncheckedTrackColor()
                )
            )
        }

    }
}
