#!/usr/bin/env bash
set -euo pipefail

log() {
  echo "[jitpack] $1"
}

require_tool() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required tool: $1" >&2
    exit 1
  fi
}
# xd

require_tool curl
require_tool mvn

PAPER_VERSION=$(mvn -q -Dexpression=paper.version -DforceStdout help:evaluate)
VELOCITY_VERSION=$(mvn -q -Dexpression=velocity.version -DforceStdout help:evaluate)

log "Detected paper.version=$PAPER_VERSION"
log "Detected velocity.version=$VELOCITY_VERSION"

install_artifact() {
  local groupId="$1"
  local artifactId="$2"
  local version="$3"
  local repository="$4"

  local groupPath=${groupId//./\/}
  local baseUrl="${repository%/}/${groupPath}/${artifactId}/${version}/${artifactId}-${version}"
  local jarPath="/tmp/${artifactId}-${version}.jar"
  local pomPath="/tmp/${artifactId}-${version}.pom"

  log "Downloading ${groupId}:${artifactId}:${version}"
  curl -sSfL "${baseUrl}.jar" -o "$jarPath"
  curl -sSfL "${baseUrl}.pom" -o "$pomPath"

  log "Installing ${groupId}:${artifactId}:${version} into local Maven cache"
  mvn -q install:install-file \
    -DgroupId="$groupId" \
    -DartifactId="$artifactId" \
    -Dversion="$version" \
    -Dpackaging=jar \
    -Dfile="$jarPath" \
    -DpomFile="$pomPath"
}

install_artifact "io.papermc.paper" "paper-api" "$PAPER_VERSION" "https://repo.papermc.io/repository/maven-public"
install_artifact "com.velocitypowered" "velocity-api" "$VELOCITY_VERSION" "https://nexus.velocitypowered.com/repository/maven-public"

log "Standalone API installation complete"
