#!/bin/bash
# 安装 Git 钩子：让仓库内的 scripts/hooks/ 生效。
#
# Git 出于安全不会在克隆后自动执行钩子，所以换机器/重新克隆后需手动跑一次：
#     bash scripts/install-hooks.sh
#
# 之后 scripts/hooks/ 下所有钩子自动生效，无需再拷贝文件。
set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

if [ ! -d scripts/hooks ]; then
  echo "✗ 找不到 scripts/hooks 目录"
  exit 1
fi

chmod +x scripts/hooks/* 2>/dev/null || true
git config core.hooksPath scripts/hooks

echo "✓ 钩子已启用：core.hooksPath = scripts/hooks"
echo ""
echo "当前生效的钩子："
for f in scripts/hooks/*; do
  [ -f "$f" ] && echo "  - $(basename "$f")"
done
echo ""
echo "验证方式：提交一条带 Co-Authored-By: traeagent 尾注的信息，应被拒绝。"
