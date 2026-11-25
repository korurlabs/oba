package upload

import (
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// uploadPage writes one page and returns its result. parentDryCreated marks a dry run below a
// page the run would create: the server cannot check a page whose parent does not exist yet, and
// a page below a new one is new itself.
func (r *run) uploadPage(page *Page, existing map[string]client.KalkanPageListItem, parentDryCreated bool) PageResult {
	if page.AssetErr != nil {
		return pageError(page.Path, page.AssetErr)
	}
	if parentDryCreated {
		return PageResult{Path: page.Path, Outcome: OutcomeCreated}
	}
	current, exists := existing[page.Path]
	if page.DirOnly && exists {
		// A directory without an index file carries no content of its own, so it never blanks
		// or conflicts with a page that people write in Kalkan.
		detail, err := r.api.GetKalkanPage(current.ID)
		if err != nil {
			return pageError(page.Path, err)
		}
		return PageResult{Path: page.Path, Outcome: OutcomeUnchanged, Version: detail.Version}
	}

	urls := map[string]string{}
	created := false
	pageID := current.ID
	if len(page.Assets) > 0 && !exists && !r.dryRun {
		// Attachments need the page's id, so a new page is created first and then written again
		// with the uploaded links.
		result, err := r.api.UpsertKalkanPage(r.upsert(page, urls), false)
		if err != nil {
			return pageError(page.Path, err)
		}
		created = true
		pageID = result.ID
	}
	if len(page.Assets) > 0 && pageID != "" {
		var err error
		if urls, err = r.resolveAssets(pageID, page); err != nil {
			return pageError(page.Path, err)
		}
	}
	result, err := r.api.UpsertKalkanPage(r.upsert(page, urls), r.dryRun)
	if err != nil {
		return pageError(page.Path, err)
	}
	outcome := strings.ToLower(result.Outcome)
	if created {
		outcome = OutcomeCreated
	}
	return PageResult{Path: page.Path, Outcome: outcome, Version: result.Version}
}

// upsert is the request that writes page, its asset links replaced by urls.
func (r *run) upsert(page *Page, urls map[string]string) client.KalkanPageUpsert {
	translations := make([]client.KalkanTranslationInput, 0, len(page.Translations))
	for _, t := range page.Translations {
		translations = append(translations, client.KalkanTranslationInput{
			Locale: t.Locale,
			Title:  t.Title,
			Body:   t.Body(page.Assets, urls),
		})
	}
	return client.KalkanPageUpsert{
		Path:         page.Path,
		Position:     page.Position,
		Compliance:   page.Compliance,
		Translations: translations,
		Force:        r.force,
	}
}
