#!/usr/bin/env bash
#
# Archive an experiment sweep as dated tarballs on a GitHub release.
#
# Usage:
#   ./scripts/archive-run.sh <sweep-name> [--session <name>]... [--draft]
#
# Example:
#   ./scripts/archive-run.sh sweep-001-gs-guides \
#       --session 20260304-045624 --session 20260304-064326 --session 20260304-112636
#
#   ./scripts/archive-run.sh sweep-001-gs-guides --draft   # archives all sessions
#
# Creates:
#   {sweep}-results.tar.gz    — session result JSONs (variant results + session metadata)
#   {sweep}-analysis.tar.gz   — analysis artifacts (tables, figures, cards, sweep report)
#   {sweep}-logs.tar.gz       — execution logs
#
# Publishes as GitHub release tagged {sweep-name}.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RESULTS_DIR="$PROJECT_ROOT/results/code-coverage-experiment"
SESSIONS_DIR="$RESULTS_DIR/sessions"
STAGING_DIR="$PROJECT_ROOT/.archive-staging"

# Parse arguments
SWEEP_NAME=""
SESSIONS=()
DRAFT_FLAG=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --session)
            SESSIONS+=("$2")
            shift 2
            ;;
        --draft)
            DRAFT_FLAG="--draft"
            shift
            ;;
        -*)
            echo "Unknown flag: $1" >&2
            exit 1
            ;;
        *)
            if [[ -z "$SWEEP_NAME" ]]; then
                SWEEP_NAME="$1"
            else
                echo "Unexpected argument: $1" >&2
                exit 1
            fi
            shift
            ;;
    esac
done

if [[ -z "$SWEEP_NAME" ]]; then
    echo "Usage: $0 <sweep-name> [--session <name>]... [--draft]"
    echo ""
    echo "Examples:"
    echo "  $0 sweep-001-gs-guides --session 20260304-045624 --session 20260304-064326"
    echo "  $0 sweep-001-gs-guides   # archives all sessions"
    echo ""
    echo "Available sessions:"
    ls "$SESSIONS_DIR" 2>/dev/null | sort
    exit 1
fi

# If no sessions specified, include all
if [[ ${#SESSIONS[@]} -eq 0 ]]; then
    echo "No --session flags provided, including all sessions"
    for d in "$SESSIONS_DIR"/*/; do
        SESSIONS+=("$(basename "$d")")
    done
fi

RESULTS_TARBALL="${SWEEP_NAME}-results.tar.gz"
ANALYSIS_TARBALL="${SWEEP_NAME}-analysis.tar.gz"
LOGS_TARBALL="${SWEEP_NAME}-logs.tar.gz"

echo "=== Archiving sweep: $SWEEP_NAME ==="
echo "Sessions: ${SESSIONS[*]}"
echo ""

# Clean staging
rm -rf "$STAGING_DIR"
mkdir -p "$STAGING_DIR/results" "$STAGING_DIR/logs"

# Package session results
echo "Packaging results..."
VARIANT_COUNT=0
for session in "${SESSIONS[@]}"; do
    session_dir="$SESSIONS_DIR/$session"
    if [[ ! -d "$session_dir" ]]; then
        echo "  WARNING: Session $session not found, skipping"
        continue
    fi
    # Copy session directory (JSONs + session metadata, skip workspaces to keep size down)
    mkdir -p "$STAGING_DIR/results/$session"
    for f in "$session_dir"/*.json; do
        [[ -f "$f" ]] && cp "$f" "$STAGING_DIR/results/$session/"
    done
    # Count variants (exclude session.json and sessions-index.json)
    for f in "$session_dir"/*.json; do
        name="$(basename "$f")"
        [[ "$name" == "session.json" || "$name" == "sessions-index.json" ]] && continue
        VARIANT_COUNT=$((VARIANT_COUNT + 1))
    done
    echo "  $session: $(ls "$STAGING_DIR/results/$session"/*.json 2>/dev/null | wc -l) files"
done
tar czf "$STAGING_DIR/$RESULTS_TARBALL" -C "$STAGING_DIR/results" .
RESULTS_SIZE="$(du -h "$STAGING_DIR/$RESULTS_TARBALL" | cut -f1)"
echo "  $RESULTS_TARBALL ($RESULTS_SIZE)"
echo ""

# Package analysis
echo "Packaging analysis..."
ANALYSIS_DIR="$PROJECT_ROOT/analysis"
if [[ -d "$ANALYSIS_DIR" ]]; then
    tar czf "$STAGING_DIR/$ANALYSIS_TARBALL" -C "$PROJECT_ROOT" analysis/
    ANALYSIS_SIZE="$(du -h "$STAGING_DIR/$ANALYSIS_TARBALL" | cut -f1)"
    echo "  $ANALYSIS_TARBALL ($ANALYSIS_SIZE)"
else
    echo "  WARNING: $ANALYSIS_DIR not found, skipping"
fi
echo ""

# Package logs
echo "Packaging logs..."
LOG_COUNT=0
for log in "$PROJECT_ROOT"/results/*.log; do
    if [[ -f "$log" ]]; then
        cp "$log" "$STAGING_DIR/logs/"
        LOG_COUNT=$((LOG_COUNT + 1))
    fi
done
if [[ $LOG_COUNT -gt 0 ]]; then
    tar czf "$STAGING_DIR/$LOGS_TARBALL" -C "$STAGING_DIR/logs" .
    LOGS_SIZE="$(du -h "$STAGING_DIR/$LOGS_TARBALL" | cut -f1)"
    echo "  $LOGS_TARBALL ($LOGS_SIZE, $LOG_COUNT log files)"
else
    echo "  No log files found, skipping"
fi
echo ""

# Generate release notes from sweep report if available
echo "Generating release notes..."
NOTES_FILE="$STAGING_DIR/release-notes.md"
SWEEP_REPORT="$ANALYSIS_DIR/sweep-001-getting-started-guides.md"

cat > "$NOTES_FILE" << EOF
## Code Coverage Experiment — $SWEEP_NAME

**Sessions**: ${SESSIONS[*]}
**Variant results**: $VARIANT_COUNT

EOF

# Pull summary table from sweep report if it exists
if [[ -f "$SWEEP_REPORT" ]]; then
    echo "### Summary Results" >> "$NOTES_FILE"
    echo "" >> "$NOTES_FILE"
    # Extract the summary table (between "## Summary Results" and the next ##)
    sed -n '/^## Summary Results/,/^## /{/^## Summary Results/d;/^## /d;p}' "$SWEEP_REPORT" >> "$NOTES_FILE" 2>/dev/null || true
    echo "" >> "$NOTES_FILE"
    echo "### High-Confidence Findings" >> "$NOTES_FILE"
    echo "" >> "$NOTES_FILE"
    sed -n '/^## High-Confidence Findings/,/^## /{/^## High-Confidence Findings/d;/^## /d;p}' "$SWEEP_REPORT" >> "$NOTES_FILE" 2>/dev/null || true
fi

# Also include the variant-comparison table
COMPARISON_TABLE="$ANALYSIS_DIR/tables/variant-comparison.md"
if [[ -f "$COMPARISON_TABLE" ]]; then
    echo "" >> "$NOTES_FILE"
    echo "### Variant Comparison" >> "$NOTES_FILE"
    echo "" >> "$NOTES_FILE"
    # Extract just the summary table
    sed -n '/^## Summary/,/^## /{/^## Summary/d;/^## /d;p}' "$COMPARISON_TABLE" >> "$NOTES_FILE"
fi

cat >> "$NOTES_FILE" << EOF

### Archives

| Archive | Contents |
|---------|----------|
| \`$RESULTS_TARBALL\` | Session result JSONs ($VARIANT_COUNT variant results) |
| \`$ANALYSIS_TARBALL\` | Analysis artifacts (tables, figures, cards, sweep report) |
| \`$LOGS_TARBALL\` | Execution logs |

### Extraction

\`\`\`bash
# Extract session results
mkdir -p results/code-coverage-experiment/sessions
tar xzf ${RESULTS_TARBALL} -C results/code-coverage-experiment/sessions/

# Extract analysis (frozen-in-time snapshot)
tar xzf ${ANALYSIS_TARBALL} -C .

# Extract logs
mkdir -p results
tar xzf ${LOGS_TARBALL} -C results/
\`\`\`

To regenerate analysis from extracted results:

\`\`\`bash
uv venv && uv pip install -r requirements.txt
.venv/bin/python scripts/load_results.py --session ${SESSIONS[*]/#/--session }
.venv/bin/python scripts/variant_comparison.py
.venv/bin/python scripts/plot_variant_radar.py
.venv/bin/python scripts/generate_item_cards.py
\`\`\`

Full sweep report: \`analysis/sweep-001-getting-started-guides.md\`
EOF

# List assets to upload
ASSETS=("$STAGING_DIR/$RESULTS_TARBALL")
[[ -f "$STAGING_DIR/$ANALYSIS_TARBALL" ]] && ASSETS+=("$STAGING_DIR/$ANALYSIS_TARBALL")
[[ -f "$STAGING_DIR/$LOGS_TARBALL" ]] && ASSETS+=("$STAGING_DIR/$LOGS_TARBALL")

echo "Ready to create release:"
echo "  Tag: $SWEEP_NAME"
echo "  Assets: ${#ASSETS[@]} files"
echo "  Draft: ${DRAFT_FLAG:-no}"
echo ""

# Create release
gh release create "$SWEEP_NAME" \
    --title "$SWEEP_NAME" \
    --notes-file "$NOTES_FILE" \
    $DRAFT_FLAG \
    "${ASSETS[@]}"

echo ""
echo "=== Release created: $SWEEP_NAME ==="
echo "View: gh release view $SWEEP_NAME"

# Clean up staging
rm -rf "$STAGING_DIR"
