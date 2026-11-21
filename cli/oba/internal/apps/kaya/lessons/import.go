package lessons

import (
	"context"
	"errors"
	"fmt"
	"unicode/utf16"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
)

// Store is the part of Kaya an import needs.
type Store interface {
	SearchLessons(ctx context.Context, remoteURL, search string) ([]client.Lesson, error)
	AddLesson(ctx context.Context, request client.LessonRequest) (client.LessonCreated, error)
}

// Result counts what an import did.
type Result struct {
	Created int
	Skipped int
}

// Import adds each block as a lesson of the checkout, or of the organization with repoWide, and
// skips a title that already exists in that scope. It refuses the whole file when a block
// breaks a limit, before it sends anything, so that a run leaves no part of the file behind for
// a reason the next run would meet again. After a failed call the result counts the calls that
// passed; running the import again goes on from there.
func Import(ctx context.Context, store Store, remoteURL string, repoWide bool, blocks []Block) (Result, error) {
	var result Result
	if err := checkBlocks(blocks); err != nil {
		return result, err
	}
	for _, block := range blocks {
		exists, err := titleExists(ctx, store, remoteURL, repoWide, block.Title)
		if err != nil {
			return result, fmt.Errorf("check %q: %w", block.Title, err)
		}
		if exists {
			result.Skipped++
			continue
		}
		request := client.LessonRequest{RemoteURL: remoteURL, Title: block.Title, Body: block.Body, RepoWide: repoWide}
		if _, err := store.AddLesson(ctx, request); err != nil {
			return result, fmt.Errorf("add %q: %w", block.Title, err)
		}
		result.Created++
	}
	return result, nil
}

func checkBlocks(blocks []Block) error {
	var problems []error
	for i, block := range blocks {
		for _, err := range []error{CheckTitle(block.Title), CheckBody(block.Body)} {
			if err != nil {
				problems = append(problems, fmt.Errorf("lesson %d %q: %w", i+1, block.Title, err))
			}
		}
	}
	return errors.Join(problems...)
}

// titleExists asks by search and not through the list, which stops at the newest 100: past
// them a second run would add every older lesson again. The search also sees organization-wide
// lessons from a repository scope and the reverse, so the scope is compared here.
func titleExists(ctx context.Context, store Store, remoteURL string, repoWide bool, title string) (bool, error) {
	found, err := store.SearchLessons(ctx, remoteURL, searchText(title))
	if err != nil {
		return false, err
	}
	for _, lesson := range found {
		if lesson.Title == title && (lesson.Repository == nil) == repoWide {
			return true, nil
		}
	}
	return false, nil
}

// searchText is title cut to what a search takes; a hit is compared by its whole title anyway.
func searchText(title string) string {
	units := 0
	for i, r := range title {
		units += utf16.RuneLen(r)
		if units > maxSearch {
			return title[:i]
		}
	}
	return title
}
