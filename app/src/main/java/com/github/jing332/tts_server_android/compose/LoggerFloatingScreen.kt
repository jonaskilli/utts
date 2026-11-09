package com.github.jing332.tts_server_android.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.github.jing332.common.LogLevel
import com.github.jing332.common.utils.limitLength
import com.github.jing332.script.runtime.console.LogListener
import com.github.jing332.script.runtime.console.LogListenerManager
import com.github.jing332.tts_server_android.compose.theme.logLevelColor
import kotlin.text.appendLine

@Composable
fun LoggerFloatingScreen(
    modifier: Modifier = Modifier,
    show: Boolean,
    registry: LogListenerManager,
    darkTheme: Boolean = isSystemInDarkTheme(),
    onDismissRequest: () -> Unit = {},
    onLaunched: () -> Unit,
) {
    var logText by remember { mutableStateOf(AnnotatedString("")) }

    // 11-10 主题化：颜色必须在 Composable 作用域里先算好再被 LogListener 捕获
    //（回调里没有 Composition，「MaterialTheme.colorScheme」拿不到）。key 带上三色，
    // 切主题/深浅时重建 listener，后续到达的行才用新色。
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
                    appendLine(entry.message.limitLength(150))
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

    ElevatedCard {
        Column() {
            if (show)
                SelectionContainer(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text =
                        logText, modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 8.dp)
                            .padding(bottom = 8.dp)
                    )
                }
        }
    }
}
