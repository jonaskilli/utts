# U·TTS Server

> 一个深度定制的 Android TTS 应用。代码由 [jing332/tts-server-android](https://github.com/jing332/tts-server-android) 首发，经 Ktouls、命无言 两代改造后，由本仓库接着往下改。

## 来源声明

**本项目并非原创作品**，而是一条接力链上的一员：

1. **jing332** —— [tts-server-android](https://github.com/jing332/tts-server-android)，项目首发，原名 TTS Server；
2. **Ktouls** —— [TTS_Server_Android2](https://cnb.cool/Ktouls/TTS_Server_Android2)（混元版），在首发代码上继续改造；
3. **命无言**（mingwuyan）—— [TTS_Server_Android](https://cnb.cool/mingwuyan/TTS_Server_Android)，在前两代基础上继续改造；
4. **本仓库**（[jonaskilli/utts](https://github.com/jonaskilli/utts)）—— 承接以上三者，继续维护与改造。

Ktouls 与命无言的 TTS **代码都实际参照过**，不只取某一段功能；每条改动的取舍与最终验收由本仓库维护者负责。各代作者的版权与署名均予保留，并在此致谢。

> 如果你要的是原版，请前往 [上游仓库](https://github.com/jing332/tts-server-android)。

## 这是什么

**U·TTS Server**，即 **「你的 TTS」** —— "U" 取 **Your** 之意：它不预设你用哪家接口、哪套音色、哪种读法，装上去改成你自己的就行；也正因为是「你的」，更适合每个人按自己的习惯用。

Android 平台的系统级 TTS 应用：

- 作为系统 TTS 引擎，供「阅读」(Legado) 等 App 直接调用朗读
- 支持自定义 HTTP 接口，接入任意在线语音合成服务
- 支持导入第三方 TTS 插件与本地 TTS 引擎
- 多角色朗读：按角色 / 场景把不同音色分给不同的人与旁白

> 包名为 `com.mumutts.app`，与上游原版、本分支旧版均互不冲突，可同时安装。

## 与上游的主要差异

> 各版本的用户向更新说明见本仓库的 [Releases](https://github.com/jonaskilli/utts/releases) 发布说明。

- **界面**：全面 Material Design 3 化（颜色 / 字号 / 形状三套 token 均接入 `MaterialTheme`），
  官方导航栏，五档圆角阶梯统一收口
- **密钥管理**：按「网址 + 密钥」分组统一管理模型，支持从接口一键拉取模型列表；
  独立的密钥池页管理轮换与整批测试
- **备份与恢复**：应用级备份 / 恢复 + 完整备份中心（覆盖式还原）
- **角色管理**：内置角色列表页，运行时自动生成，支持就地换声
- **JRead 兼容**：导入 JRead（墨听）语音配置时，自动映射标签、分池与补绑
- **音频参数**：多层增益 / 语速 / 音调，试听与朗读走同一链路、实时生效
- **朗读日志**：日志行内就地换声、调参、试听，与角色表互通；
  状态用纯文字表达而非色块，语音信息按「角色 / 发音人 / 插件」分色

## 致谢

除了上游作者 **jing332**，本项目还参照了以下成果：

| 来源 | 贡献 |
|---|---|
| **Ktouls** | TTS Server 衍生实现（混元版，接力链第二棒） · [cnb.cool/Ktouls/TTS_Server_Android2](https://cnb.cool/Ktouls/TTS_Server_Android2) |
| **mingwuyan**（命無言） | 角色管理与标签体系（接力链第三棒；代码内沿用其 `ruleId = mingwuyan` 命名）· [cnb.cool/mingwuyan](https://cnb.cool/mingwuyan/TTS_Server_Android) |
| **墨听** | [JRead-VoiceEngine](https://github.com/jwoo1982217-eng/JRead-VoiceEngine) —— 语音引擎分支，本项目 JRead 兼容层的参照 |
| **阅读 Sigma** | [Rimchars/legado](https://github.com/Rimchars/legado) —— 本项目响度均衡等设计的参照 |
| **dsh-Go** | [ccch1mneyyy/dsh-Go](https://github.com/ccch1mneyyy/dsh-Go) —— 配色体系参照（OKLCH 明度表 + 每套只换色相与彩度的推导方式），见 `theme/Color2.kt` 顶部说明 |

以上仅为成果归属说明；各组件请遵循其各自仓库的许可。

## 许可

本项目以 **GNU General Public License v3.0**（GPL-3.0）分发，全文见 [`LICENSE`](LICENSE)。

- 基于 [jing332/tts-server-android](https://github.com/jing332/tts-server-android)（原作者 jing332，MIT）二次开发，原作者的版权声明一并保留
- 参考了 [Rimchars/legado](https://github.com/Rimchars/legado)（阅读 Sigma，GPL-3.0）与 [JRead-VoiceEngine](https://github.com/jwoo1982217-eng/JRead-VoiceEngine)（墨听，GPL-3.0）的部分实现

分发或再修改时，请同样以 GPL-3.0 开放源代码。
