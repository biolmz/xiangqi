#!/usr/bin/env bash
# 一键发布：建仓库 → 推代码 → 建 release 传附件 → 开 GitHub Pages
# 用法： GITHUB_TOKEN=xxx bash publish.sh
set -euo pipefail

OWNER="${OWNER:-biolmz}"
REPO="${REPO:-xiangqi}"
TAG="${TAG:-v1.0.1}"
DESC="纯 Java 中国象棋，Windows / macOS / Android 三端"

cd "$(dirname "$0")"

if [ -z "${GITHUB_TOKEN:-}" ]; then
  echo "缺少 GITHUB_TOKEN" >&2
  exit 1
fi
export GH_TOKEN="$GITHUB_TOKEN"

echo "==> 1/5 校验登录"
gh api user -q .login

echo "==> 2/5 创建或复用仓库 $OWNER/$REPO"
if gh repo view "$OWNER/$REPO" >/dev/null 2>&1; then
  echo "    仓库已存在，复用"
else
  gh repo create "$OWNER/$REPO" --public --description "$DESC"
fi

echo "==> 3/5 推送 main"
if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "https://github.com/$OWNER/$REPO.git"
else
  git remote add origin "https://github.com/$OWNER/$REPO.git"
fi
git push -u origin main --force

echo "==> 4/5 创建 release $TAG 并上传附件"
if gh release view "$TAG" --repo "$OWNER/$REPO" >/dev/null 2>&1; then
  echo "    release 已存在，改为重传附件"
  gh release upload "$TAG" release/* --repo "$OWNER/$REPO" --clobber
else
  gh release create "$TAG" \
    --repo "$OWNER/$REPO" \
    --title "中国象棋 $TAG" \
    --notes-file RELEASE_NOTES.md \
    release/*
fi

echo "==> 5/5 开启 GitHub Pages（/docs）"
if gh api "repos/$OWNER/$REPO/pages" >/dev/null 2>&1; then
  gh api -X PUT "repos/$OWNER/$REPO/pages" \
    -f 'source[branch]=main' -f 'source[path]=/docs' >/dev/null && echo "    已更新 Pages 配置"
else
  gh api -X POST "repos/$OWNER/$REPO/pages" \
    -f 'source[branch]=main' -f 'source[path]=/docs' >/dev/null && echo "    已开启 Pages"
fi

echo
echo "仓库   https://github.com/$OWNER/$REPO"
echo "Release https://github.com/$OWNER/$REPO/releases/tag/$TAG"
echo "页面   https://$OWNER.github.io/$REPO/  （首次构建约 1 分钟）"
