#!/usr/bin/env bash
#
# Materialize dataset workspaces from source repos.
# Clones each repo (shallow) into workspaces/ and copies the source
# subdirectory into items/{id}/before/ for the experiment framework.
#
# Supports two item types:
#   - Spring guides: source at {repo}/complete/
#   - Standalone projects: source at repo root
#
# Usage: ./dataset/materialize.sh [--verify]
#   --verify  Also runs ./mvnw clean compile test in each before/ directory

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORKSPACES_DIR="$SCRIPT_DIR/workspaces"
ITEMS_DIR="$SCRIPT_DIR/items"

# Spring Getting Started guides (spring-guides org, complete/ subdirectory)
GUIDES=(
  gs-rest-service
  gs-accessing-data-jpa
  gs-securing-web
  gs-reactive-rest-service
  gs-messaging-stomp-websocket
)

# Standalone projects: slug|clone_url|source_subdir
PROJECTS=(
  "spring-petclinic|https://github.com/spring-projects/spring-petclinic.git|."
)

VERIFY=false
if [[ "${1:-}" == "--verify" ]]; then
  VERIFY=true
fi

echo "=== Materializing dataset workspaces ==="
echo "Workspaces: $WORKSPACES_DIR"
echo "Items:      $ITEMS_DIR"
echo ""

# Collect all item slugs and their source info into parallel arrays
ALL_SLUGS=()
ALL_URLS=()
ALL_SUBDIRS=()

for guide in "${GUIDES[@]}"; do
  ALL_SLUGS+=("$guide")
  ALL_URLS+=("https://github.com/spring-guides/${guide}.git")
  ALL_SUBDIRS+=("complete")
done

for entry in "${PROJECTS[@]}"; do
  IFS='|' read -r slug url subdir <<< "$entry"
  ALL_SLUGS+=("$slug")
  ALL_URLS+=("$url")
  ALL_SUBDIRS+=("$subdir")
done

# Step 1: Clone repos
mkdir -p "$WORKSPACES_DIR"
for i in "${!ALL_SLUGS[@]}"; do
  slug="${ALL_SLUGS[$i]}"
  url="${ALL_URLS[$i]}"
  if [[ -d "$WORKSPACES_DIR/$slug" ]]; then
    echo "[$slug] Already cloned, skipping"
  else
    echo "[$slug] Cloning from $url..."
    git clone --depth 1 "$url" "$WORKSPACES_DIR/$slug"
  fi
done

echo ""

# Step 2: Copy source into items/{id}/before/
for i in "${!ALL_SLUGS[@]}"; do
  slug="${ALL_SLUGS[$i]}"
  subdir="${ALL_SUBDIRS[$i]}"
  BEFORE_DIR="$ITEMS_DIR/$slug/before"

  if [[ "$subdir" == "." ]]; then
    SOURCE_DIR="$WORKSPACES_DIR/$slug"
  else
    SOURCE_DIR="$WORKSPACES_DIR/$slug/$subdir"
  fi

  if [[ ! -d "$SOURCE_DIR" ]]; then
    echo "[$slug] ERROR: $SOURCE_DIR not found"
    exit 1
  fi

  if [[ -d "$BEFORE_DIR" ]]; then
    echo "[$slug] Removing existing before/"
    rm -rf "$BEFORE_DIR"
  fi

  echo "[$slug] Copying $subdir/ -> before/"
  if [[ "$subdir" == "." ]]; then
    # Copy repo root, excluding .git/
    rsync -a --exclude='.git' "$SOURCE_DIR/" "$BEFORE_DIR/"
  else
    cp -r "$SOURCE_DIR" "$BEFORE_DIR"
  fi
  FILE_COUNT=$(find "$BEFORE_DIR" -type f | wc -l)
  echo "[$slug] $FILE_COUNT files copied"
done

echo ""

# Step 2a: Save reference tests (developers' gold standard)
echo "=== Saving reference tests ==="
for slug in "${ALL_SLUGS[@]}"; do
  BEFORE_DIR="$ITEMS_DIR/$slug/before"
  REF_DIR="$ITEMS_DIR/$slug/reference"

  if [[ -d "$REF_DIR" ]]; then
    rm -rf "$REF_DIR"
  fi

  if [[ -d "$BEFORE_DIR/src/test" ]]; then
    mkdir -p "$REF_DIR/src"
    cp -r "$BEFORE_DIR/src/test" "$REF_DIR/src/test"
    REF_COUNT=$(find "$REF_DIR" -name "*.java" -type f | wc -l)
    echo "[$slug] Saved $REF_COUNT reference test files"
  else
    echo "[$slug] WARNING: No src/test/ found — skipping reference save"
  fi
done

echo ""

# Step 2b: Strip tests from before/ (agent writes from scratch)
echo "=== Stripping tests from before/ ==="
for slug in "${ALL_SLUGS[@]}"; do
  BEFORE_DIR="$ITEMS_DIR/$slug/before"
  TEST_JAVA_DIR="$BEFORE_DIR/src/test/java"

  if [[ -d "$TEST_JAVA_DIR" ]]; then
    rm -rf "$TEST_JAVA_DIR"
    mkdir -p "$TEST_JAVA_DIR"
    echo "[$slug] Stripped test sources (kept src/test/resources/)"
  else
    echo "[$slug] No src/test/java/ to strip"
  fi
done

echo ""

# Step 3: Optional verification
if [[ "$VERIFY" == true ]]; then
  echo "=== Verifying builds ==="
  for slug in "${ALL_SLUGS[@]}"; do
    BEFORE_DIR="$ITEMS_DIR/$slug/before"
    echo "[$slug] Running ./mvnw clean compile..."
    if (cd "$BEFORE_DIR" && ./mvnw clean compile -q); then
      echo "[$slug] BUILD SUCCESS"
    else
      echo "[$slug] BUILD FAILED"
      exit 1
    fi
  done
fi

echo ""
echo "=== Materialization complete ==="
echo "Items: ${ALL_SLUGS[*]}"
