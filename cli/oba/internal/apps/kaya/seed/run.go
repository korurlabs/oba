package seed

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"os"
	"sync"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
)

// uploadWorkers bounds the parallel blob uploads.
const uploadWorkers = 4

// Send posts the seed run. When the server answers that blobs are missing (KAY_CFG_009), it
// uploads exactly those and posts once more, so a repeated seed uploads nothing it already holds.
// It returns the run and the number of blobs uploaded; a refusal with field reasons is a *Refusal.
func Send(ctx context.Context, api *client.Client, plan Plan) (client.SeedRun, int, error) {
	run, err := api.CreateSeedRun(ctx, plan.Request)
	if err == nil {
		return run, 0, nil
	}
	missing := client.MissingBlobs(err)
	if missing == nil {
		return client.SeedRun{}, 0, explainRefusal(err)
	}
	if err := upload(ctx, api, plan, missing); err != nil {
		return client.SeedRun{}, 0, err
	}
	run, err = api.CreateSeedRun(ctx, plan.Request)
	if err != nil {
		return client.SeedRun{}, len(missing), explainRefusal(err)
	}
	return run, len(missing), nil
}

func upload(ctx context.Context, api *client.Client, plan Plan, missing []string) error {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	jobs := make(chan string)
	var (
		wg       sync.WaitGroup
		mu       sync.Mutex
		firstErr error
	)
	fail := func(err error) {
		mu.Lock()
		defer mu.Unlock()
		if firstErr == nil {
			firstErr = err
			cancel()
		}
	}
	for range uploadWorkers {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for sha := range jobs {
				if err := uploadOne(ctx, api, plan, sha); err != nil {
					fail(err)
				}
			}
		}()
	}
	for _, sha := range missing {
		if ctx.Err() != nil {
			break
		}
		jobs <- sha
	}
	close(jobs)
	wg.Wait()
	return firstErr
}

func uploadOne(ctx context.Context, api *client.Client, plan Plan, sha string) error {
	entry, ok := plan.Blobs[sha]
	if !ok {
		return fmt.Errorf("the server asks for blob %s, which this seed does not name", sha)
	}
	content, err := os.ReadFile(entry.Source)
	if err != nil {
		return fmt.Errorf("read %s for upload: %w", entry.Source, err)
	}
	// The file may have changed since the scan; uploading other bytes under the scanned hash
	// would only be refused by the server, so say which file moved.
	sum := sha256.Sum256(content)
	if hex.EncodeToString(sum[:]) != sha {
		return fmt.Errorf("%s changed during the seed; run it again", entry.Source)
	}
	if _, err := api.UploadBlob(ctx, sha, content); err != nil {
		return fmt.Errorf("upload %s: %w", entry.Path, err)
	}
	return nil
}

// Check compares, per repository, the release the run leaves current with the repository on disk:
// the release the run created, or the newest one when the run changed nothing for it. The
// repositories are found by their normalised remote, as the server stores them.
func Check(ctx context.Context, api *client.Client, plan Plan, run client.SeedRun) ([]RepositoryCheck, error) {
	if !run.Report.Applied || len(plan.Repositories) == 0 {
		return nil, nil
	}
	created := map[string]int{}
	for _, release := range run.Report.Releases {
		created[release.RepositoryID] = release.Number
	}
	stored, err := api.Repositories(ctx)
	if err != nil {
		return nil, fmt.Errorf("list the repositories: %w", err)
	}
	ids := map[string]string{}
	for _, repo := range stored {
		ids[repo.RemoteURL] = repo.ID
	}
	checks := make([]RepositoryCheck, 0, len(plan.Repositories))
	for _, repo := range plan.Repositories {
		check := RepositoryCheck{Repository: repo.Name}
		id := ids[NormalizeRemote(repo.RemoteURL)]
		if id == "" {
			check.Skipped = "Kaya has no repository with the remote " + repo.RemoteURL
			checks = append(checks, check)
			continue
		}
		var release *client.Release
		if number, ok := created[id]; ok {
			release, err = api.Release(ctx, id, number)
		} else {
			release, err = api.LatestRelease(ctx, id)
		}
		if err != nil {
			return nil, fmt.Errorf("read the release of %s: %w", repo.Name, err)
		}
		if release == nil {
			check.Skipped = "the repository has no release"
			checks = append(checks, check)
			continue
		}
		check.ReleaseNumber = release.Number
		for _, mismatch := range Compare(*release, repo.Disk, repo.CutOver) {
			if why, designed := repo.OnPurpose[mismatch.Path]; designed {
				mismatch.Reason += ", " + why
				check.OnPurpose = append(check.OnPurpose, mismatch)
				continue
			}
			check.Mismatches = append(check.Mismatches, mismatch)
		}
		checks = append(checks, check)
	}
	return checks, nil
}

// Passed reports whether the run passed and every compared release equals its disk, apart from
// the paths a seed layout changes on purpose.
func Passed(run client.SeedRun, checks []RepositoryCheck) bool {
	if run.Status != client.SeedPassed {
		return false
	}
	for _, check := range checks {
		if check.Skipped != "" || len(check.Mismatches) > 0 {
			return false
		}
	}
	return true
}
