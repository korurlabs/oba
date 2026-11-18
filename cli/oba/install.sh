#!/bin/sh
set -eu

REPO="korurlabs/oba-cli"
PROJECT="oba-cli"
BINARY="oba"

err() { printf 'error: %s\n' "$*" >&2; exit 1; }
info() { printf '==> %s\n' "$*"; }

for cmd in curl tar uname awk mktemp mkdir mv chmod rm; do
  command -v "$cmd" >/dev/null 2>&1 || err "required command '$cmd' not found in PATH"
done

case "$(uname -s)" in
  Darwin) OS=darwin ;;
  Linux) OS=linux ;;
  *) err "unsupported operating system; the oba CLI supports macOS and Linux" ;;
esac
case "$(uname -m)" in
  x86_64|amd64) ARCH=amd64 ;;
  arm64|aarch64) ARCH=arm64 ;;
  *) err "unsupported CPU architecture; the oba CLI supports amd64 and arm64" ;;
esac

if command -v sha256sum >/dev/null 2>&1; then
  hash_file() { sha256sum "$1" | awk '{print $1}'; }
elif command -v shasum >/dev/null 2>&1; then
  hash_file() { shasum -a 256 "$1" | awk '{print $1}'; }
else
  err "sha256sum or shasum is required to verify the download"
fi

RELEASE_JSON="$(curl -fsSL "https://api.github.com/repos/${REPO}/releases/latest")" \
  || err "could not resolve the latest release from GitHub"
TAG="$(printf '%s\n' "$RELEASE_JSON" | awk -v RS=',' -F '"' '/"tag_name"[[:space:]]*:/ {print $4; exit}')"
printf '%s\n' "$TAG" | awk '/^v[0-9]+\.[0-9]+\.[0-9]+$/ {valid=1} END {exit !valid}' \
  || err "GitHub did not return a stable release tag"
VERSION="${TAG#v}"
ARCHIVE="${PROJECT}_${VERSION}_${OS}_${ARCH}.tar.gz"
CHECKSUMS="${PROJECT}_${VERSION}_checksums.txt"
BASE_URL="https://github.com/${REPO}/releases/download/${TAG}"
WORKDIR="$(mktemp -d)" || err "could not create a temporary directory"
trap 'rm -rf "$WORKDIR"' EXIT
trap 'exit 1' HUP INT TERM

info "downloading the oba CLI ${TAG} for ${OS}/${ARCH}"
curl -fsSL -o "$WORKDIR/$ARCHIVE" "$BASE_URL/$ARCHIVE" || err "failed to download $ARCHIVE"
curl -fsSL -o "$WORKDIR/$CHECKSUMS" "$BASE_URL/$CHECKSUMS" || err "failed to download checksums"
EXPECTED_SUM="$(awk -v file="$ARCHIVE" '$2 == file {sum=$1; count++} END {if (count == 1) print sum}' "$WORKDIR/$CHECKSUMS")"
[ "${#EXPECTED_SUM}" -eq 64 ] || err "missing or duplicate checksum for $ARCHIVE"
ACTUAL_SUM="$(hash_file "$WORKDIR/$ARCHIVE")" || err "could not compute the archive checksum"
[ "$EXPECTED_SUM" = "$ACTUAL_SUM" ] || err "checksum mismatch for $ARCHIVE; nothing was installed"

tar -xzf "$WORKDIR/$ARCHIVE" -C "$WORKDIR" "$BINARY" || err "could not extract $BINARY"
[ -f "$WORKDIR/$BINARY" ] && [ ! -L "$WORKDIR/$BINARY" ] || err "archive does not contain a regular $BINARY binary"
chmod +x "$WORKDIR/$BINARY"
"$WORKDIR/$BINARY" --version || err "downloaded binary failed to run"

INSTALL_DIR="${OBA_INSTALL_DIR:-}"
if [ -z "$INSTALL_DIR" ]; then
  if [ -w /usr/local/bin ]; then
    INSTALL_DIR=/usr/local/bin
  else
    INSTALL_DIR="${HOME:?HOME is required for a user installation}/.local/bin"
  fi
fi
mkdir -p "$INSTALL_DIR" || err "could not create $INSTALL_DIR"
[ ! -d "$INSTALL_DIR/$BINARY" ] || err "$INSTALL_DIR/$BINARY is a directory"
mv -f "$WORKDIR/$BINARY" "$INSTALL_DIR/$BINARY" || err "could not install in $INSTALL_DIR; set OBA_INSTALL_DIR to a writable directory"
info "installed the oba CLI $TAG in $INSTALL_DIR/$BINARY"
case ":$PATH:" in
  *":$INSTALL_DIR:"*) ;;
  *) printf "Add this directory to PATH:\n  export PATH=\"%s:\$PATH\"\n" "$INSTALL_DIR" ;;
esac
