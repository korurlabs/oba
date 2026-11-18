package client

import (
	"fmt"
	"net/url"
)

// updatesPath is the collection of daily updates. An author's updates span organizations, so
// the API needs no organization header on it and checks any organization it is given.
const updatesPath = kazanPath + "/updates"

func updatePath(id string) string {
	return updatesPath + "/" + url.PathEscape(id)
}

// SuggestedUpdateTickets reads the tickets proposed for the caller's update: those assigned to
// the caller and IN_PROGRESS on the caller's boards, sub-tickets included.
func (c *Client) SuggestedUpdateTickets() ([]Ticket, error) {
	body, err := c.Get(updatesPath + "/suggested-tickets")
	if err != nil {
		return nil, err
	}
	return parseData[[]Ticket](body, "the suggested tickets")
}

// PreviewDailyUpdate renders a new update without saving it.
func (c *Client) PreviewDailyUpdate(req DailyUpdateRequest) (DailyUpdatePreview, error) {
	body, err := c.Post(updatesPath+"/preview", req)
	if err != nil {
		return DailyUpdatePreview{}, err
	}
	return parseData[DailyUpdatePreview](body, "the update preview")
}

// CreateDailyUpdate posts a new update.
func (c *Client) CreateDailyUpdate(req DailyUpdateRequest) (DailyUpdate, error) {
	body, err := c.Post(updatesPath, req)
	if err != nil {
		return DailyUpdate{}, err
	}
	return parseData[DailyUpdate](body, "the posted update")
}

// ListDailyUpdates reads the one page of updates query selects, newest first. Unlike the other
// lists it is read page by page, since the caller chooses the page.
func (c *Client) ListDailyUpdates(query url.Values) (DailyUpdatePage, error) {
	body, err := c.Get(withQuery(updatesPath, query))
	if err != nil {
		return DailyUpdatePage{}, err
	}
	updates, totals, err := parsePage[DailyUpdate](body, "the updates")
	if err != nil {
		return DailyUpdatePage{}, err
	}
	return DailyUpdatePage{
		Updates: updates, CurrentPage: totals.CurrentPage,
		TotalPages: totals.TotalPages, TotalElements: totals.TotalElements,
	}, nil
}

// GetDailyUpdate reads one update the caller can see.
func (c *Client) GetDailyUpdate(id string) (DailyUpdate, error) {
	body, err := c.Get(updatePath(id))
	if err != nil {
		return DailyUpdate{}, err
	}
	return parseData[DailyUpdate](body, fmt.Sprintf("update %s", id))
}

// PreviewDailyUpdateEdit renders an edit of update id without saving it.
func (c *Client) PreviewDailyUpdateEdit(id string, req DailyUpdateRequest) (DailyUpdatePreview, error) {
	body, err := c.Post(updatePath(id)+"/preview", req)
	if err != nil {
		return DailyUpdatePreview{}, err
	}
	return parseData[DailyUpdatePreview](body, "the update preview")
}

// EditDailyUpdate replaces the content of update id with req.
func (c *Client) EditDailyUpdate(id string, req DailyUpdateRequest) (DailyUpdate, error) {
	body, err := c.Put(updatePath(id), req)
	if err != nil {
		return DailyUpdate{}, err
	}
	return parseData[DailyUpdate](body, "the saved update")
}

// DeleteDailyUpdate deletes update id. Ticket progress it set stays.
func (c *Client) DeleteDailyUpdate(id string) error {
	_, err := c.Delete(updatePath(id))
	return err
}

// MissingDailyUpdates reads who has not posted an update in the organization and on the day
// query names.
func (c *Client) MissingDailyUpdates(query url.Values) (DailyUpdateMissing, error) {
	body, err := c.Get(withQuery(updatesPath+"/missing", query))
	if err != nil {
		return DailyUpdateMissing{}, err
	}
	return parseData[DailyUpdateMissing](body, "the missing updates")
}
