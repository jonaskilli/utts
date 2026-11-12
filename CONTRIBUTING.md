# 提交规范

依据 [约定式提交 1.0.0](https://www.conventionalcommits.org/zh-hans/v1.0.0/)（Conventional Commits）。

## 格式

```
<类型>[可选范围]: <描述>

[可选正文]

[可选脚注]
```

- 冒号为英文半角，其后必须跟一个空格。
- 类型全小写，本项目统一使用小写。
- 描述为对变更为的简短总结，结尾不加句号。

## 类型

| 类型 | 用途 |
|---|---|
| `feat` | 新增功能 |
| `fix` | 修复缺陷 |
| `docs` | 仅文档变更 |
| `style` | 格式调整，不影响逻辑（缩进、空格、分号等） |
| `refactor` | 重构，既不修缺陷也不加功能 |
| `perf` | 性能优化 |
| `test` | 测试用例的增删改 |
| `build` | 构建系统或外部依赖变更 |
| `ci` | 持续集成配置变更 |
| `chore` | 其它不修改业务代码的杂项 |

## 范围

范围为描述代码所属部分的名词，置于圆括号内。本仓库常用的范围：

`tts`（朗读）、`log`（日志）、`script`（脚本引擎）、`ui`、`build`（构建）、`ci`、`docs`。

## 破坏性变更

在类型或类型(范围)后紧跟 `!`，或在脚注中写 `BREAKING CHANGE: <说明>`。

```
feat(read)!: 翻页手势改为可配置
```

## 正文与脚注

- 正文起始于描述后的一个空行，用于说明动机、根因与影响。
- 引用用户决定时写明日期，例如 `（用户定案 11-12）`。
- 引用 CI 时写明运行编号，例如 `（CI 43031934592）`。
- 脚注采用 `令牌: 值` 形式，如 `Refs: #123`。

## 禁止 AI 工具署名

提交信息中**禁止**出现指向 AI 工作平台的 `Co-Authored-By` 尾注：

```
Co-Authored-By: traeagent <traeagent@users.noreply.github.com>    ❌
Co-Authored-By: Claude Code <noreply@anthropic.com>              ❌
Co-Authored-By: Cursor Agent <...>                              ❌
```

**原因**：GitHub 会把 `Co-Authored-By` 中的账号计入仓库 Contributors。
Trae、Claude Code、Cursor 这类 AI 工作平台生成的尾注，会让 AI 以「贡献者」身份
出现在仓库首页——本项目不是 AI 的作品，贡献者只应是人类维护者。

### 已配置的防护

仓库自带 `commit-msg` 钩子（`scripts/hooks/commit-msg`），提交时自动拦截
含 `trae|cursor|claude|copilot|codeium|windsurf|aider|devin|openai|anthropic|gemini|glm|deepseek|chatgpt`
等关键词的 `Co-Authored-By` 行，命中即拒绝提交。

钩子本体在仓库内 `scripts/hooks/commit-msg`，随仓库分发、有提交记录可追溯。
Git 出于安全不会在克隆后自动执行钩子，**换机器或重新克隆后需启用一次**：

```bash
bash scripts/install-hooks.sh
```

该脚本会设置 `core.hooksPath=scripts/hooks`，之后该目录下所有钩子自动生效，
无需逐个拷贝文件。验证：提交一条带 `Co-Authored-By: traeagent` 尾注的信息，
应被拒绝并提示删除尾注。

### 已经混入历史的尾注怎么处理

若发现 Contributors 列表出现 AI 账号，说明历史里已有此类尾注。处理方式：

```bash
git branch backup-before-ai-trailer-removal   # 先备份
git filter-branch -f --msg-filter '<删尾注的脚本>' <最早受影响提交>^..HEAD
```

⚠️ 改写会使之后所有提交的 hash 变化，**若有发布标签指向这些提交，必须一并重指**并强制推送：

```bash
git tag -f <标签名> <新提交sha>
git push --force origin <分支>
git push --force origin <标签1> <标签2> ...
```

处理完用 `curl` 或 API 查 `repos/<owner>/<repo>/contributors` 确认只剩人类账号。
