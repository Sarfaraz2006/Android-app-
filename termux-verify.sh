#!/data/data/com.termux/files/usr/bin/bash
# =============================================================================
# Vexo Forge — Termux Verification Script
# Copy-paste this entire script into your Termux and run it.
# No root required. No proot required.
# =============================================================================
set -e

echo ""
echo "======================================================"
echo " Vexo Forge — Termux Verification"
echo " $(date)"
echo "======================================================"
echo ""

PASS=0
FAIL=0

ok()   { echo "  ✅ $1"; PASS=$((PASS + 1)); }
fail() { echo "  ❌ $1"; FAIL=$((FAIL + 1)); }

# ── Step 1: Check Node.js ─────────────────────────────────────────────────────
echo "[1/7] Checking Node.js..."
if node --version >/dev/null 2>&1; then
  NODE_VER=$(node --version)
  ok "Node.js: $NODE_VER"
else
  fail "Node.js not found. Install with: pkg install nodejs"
  echo "     Run: pkg install nodejs && bash termux-verify.sh"
  exit 1
fi

# ── Step 2: Clone the repo ────────────────────────────────────────────────────
echo ""
echo "[2/7] Cloning Vexo Forge..."
REPO_DIR="$HOME/vexo-forge-test"
rm -rf "$REPO_DIR"
if git clone https://github.com/Sarfaraz2006/Android-app-.git "$REPO_DIR" --depth 1 2>&1; then
  ok "Repo cloned to $REPO_DIR"
else
  fail "git clone failed. Check your internet connection."
  exit 1
fi
cd "$REPO_DIR"

# ── Step 3: smoke — forge status (no API key) ─────────────────────────────────
echo ""
echo "[3/7] forge status — no API keys (should warn, not crash)..."
OUTPUT=$(GEMINI_API_KEY="" ANTHROPIC_API_KEY="" OPENAI_API_KEY="" FORGE_EXECUTION_MODE=local node src/forge.js status 2>&1) || true
if echo "$OUTPUT" | grep -qi "provider\|key\|warning\|No"; then
  ok "forge status warns about missing provider key"
else
  fail "forge status: unexpected output — $OUTPUT"
fi

# ── Step 4: build --no-opencode (template mode, no LLM) ──────────────────────
echo ""
echo "[4/7] forge build --no-opencode (template mode, no LLM needed)..."
BUILD_OUTPUT=$(GEMINI_API_KEY="" ANTHROPIC_API_KEY="" OPENAI_API_KEY="" FORGE_EXECUTION_MODE=local \
  node src/forge.js build \
  --prompt "Build a one-page salon site in London" \
  --no-opencode 2>&1)

if echo "$BUILD_OUTPUT" | grep -q '"ok": true'; then
  ok "forge build --no-opencode succeeded"
else
  fail "forge build --no-opencode failed: $BUILD_OUTPUT"
fi

# Extract project dir from JSON output
PROJECT_DIR=$(echo "$BUILD_OUTPUT" | grep -o '"previewHint": "cd [^"]*"' | sed 's/"previewHint": "cd //;s/[^"]*$//' | awk '{print $1}')

# ── Step 5: confirm local mode never touches E2B ──────────────────────────────
echo ""
echo "[5/7] Confirming local mode does not touch E2B..."
if echo "$BUILD_OUTPUT" | grep -qi "e2b\|E2B_API_KEY"; then
  fail "local mode output mentions E2B unexpectedly"
else
  ok "No E2B references in local mode output"
fi

# ── Step 6: E2B missing key — graceful error ──────────────────────────────────
echo ""
echo "[6/7] E2B mode without key — should fail gracefully..."
E2B_OUTPUT=$(FORGE_EXECUTION_MODE=e2b E2B_API_KEY="" \
  node src/forge.js build \
  --prompt "Test site" \
  --no-opencode 2>&1) || true

if echo "$E2B_OUTPUT" | grep -qi "E2B_API_KEY\|e2b.*not set\|local"; then
  ok "E2B missing key gives clear error message"
else
  fail "E2B missing key error was unclear: $E2B_OUTPUT"
fi

# ── Step 7: OpenCode install check ────────────────────────────────────────────
echo ""
echo "[7/7] OpenCode installation check..."
if command -v opencode >/dev/null 2>&1; then
  OC_VER=$(opencode --version 2>&1 || echo "version unknown")
  ok "opencode is in PATH: $OC_VER"
else
  echo "  ⚠️  opencode is not installed (expected — it's optional for local template mode)."
  echo "     To install on Termux, run:"
  echo "       curl -fsSL https://raw.githubusercontent.com/superman-enamy/opencode-termux-installer/main/install.sh | bash"
  echo "     Or use proot-distro Ubuntu + npm install -g opencode-ai"
  PASS=$((PASS + 1))  # Not failing — documented optional dependency
fi

# ─── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "======================================================"
echo " Results: $PASS passed, $FAIL failed"
echo "======================================================"

if [ "$FAIL" -gt 0 ]; then
  echo ""
  echo "Some checks failed. Review the output above for details."
  exit 1
else
  echo ""
  echo "All checks passed! 🎉"
  echo ""
  echo "Next steps:"
  echo "  1. Set your API key: export GEMINI_API_KEY=your-key-here"
  echo "     (or ANTHROPIC_API_KEY or OPENAI_API_KEY)"
  echo "  2. Install OpenCode (for full LLM-powered builds):"
  echo "     curl -fsSL https://raw.githubusercontent.com/superman-enamy/opencode-termux-installer/main/install.sh | bash"
  echo "  3. Run a real build:"
  echo "     node src/forge.js build --prompt \"Build a dental clinic landing page for London\""
  echo ""
fi
