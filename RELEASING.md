# Releasing Oba

A release is one Git tag. Pushing a tag `vX.Y.Z` (or `vX.Y.Z-rc.N` for a release candidate) runs `.github/workflows/release.yml`, which publishes everything from that commit.

## Cut a release

1. Make sure the commit you release on `main` passed its checks. The release builds the images without running the tests again.
2. Pick the next version by semantic versioning. A database migration that the previous release cannot run on is a breaking change (see Upgrades in `charts/oba/README.md`).
3. Tag and push:

   ```sh
   git switch main && git pull
   git tag -a v1.4.0 -m "Oba 1.4.0"
   git push origin v1.4.0
   ```

4. Watch the Release workflow in the Actions tab. When it is green, edit the generated release notes on GitHub if they need it.

A tag that is not `vX.Y.Z` or `vX.Y.Z-<prerelease>` fails the first job and publishes nothing. To redo a failed release, fix the cause on `main` and cut the next patch version; do not move a pushed tag.

## What gets published

All under one version number `X.Y.Z`:

| What | Where |
|---|---|
| Seven images: `api`, `kazan`, `kaya`, `kalkan`, `otak`, `admin`, `account` | `ghcr.io/korurlabs/oba-<component>:X.Y.Z`, and `:latest` for a stable version |
| The Helm chart, `version` and `appVersion` `X.Y.Z` | `oci://ghcr.io/korurlabs/charts/oba` |
| Artifact Hub metadata | `ghcr.io/korurlabs/charts/oba:artifacthub.io` |
| A GitHub release with the chart package and install notes | the repository's Releases page, marked pre-release for an `-rc` tag |

The chart is pushed only after all seven images, so a chart version never points at a missing image. A pre-release tag never moves `latest`.

Install a release:

```sh
helm install oba oci://ghcr.io/korurlabs/charts/oba --version X.Y.Z -f my-values.yaml
```

## Release the oba CLI

The oba CLI in `cli/oba` has its own version and its own tag, `oba-cli/vX.Y.Z` (or `oba-cli/vX.Y.Z-rc.N`). Pushing it runs `.github/workflows/cli-oba-release.yml`, which tests the module and publishes with goreleaser (`cli/oba/.goreleaser.yaml`):

```sh
git switch main && git pull
git tag -a oba-cli/v0.4.0 -m "oba CLI 0.4.0"
git push origin oba-cli/v0.4.0
```

| What | Where |
|---|---|
| Archives for macOS and Linux on amd64 and arm64, checksums, `install.sh` | release `vX.Y.Z` of `korurlabs/oba-cli` |
| The Homebrew formula, stable versions only | `Formula/oba-cli.rb` in `korurlabs/homebrew-tap` (`brew install korurlabs/tap/oba-cli`) |

The version must be higher than the latest release of `korurlabs/oba-cli`, or the workflow stops before it publishes. goreleaser first runs `TestReleaseTrustsAKey` (build tag `release`): it fails the release while `releaseKeys` in `cli/oba/internal/apps/kaya/delivery/keys.go` holds no production public key of Kaya's config signing key, because a binary without one refuses every config release `oba kaya` delivers. A manual run of the workflow only checks that the release GitHub App can still write to both repositories.

The workflow runs in the GitHub environment `release` and needs, in that environment, the variable `RELEASE_APP_CLIENT_ID` (client id of a GitHub App installed on `korurlabs/oba-cli` and `korurlabs/homebrew-tap` with contents read and write) and the secret `RELEASE_APP_PRIVATE_KEY` (that App's private key). `korurlabs/oba-cli` must hold no tag-triggered workflow of its own.
