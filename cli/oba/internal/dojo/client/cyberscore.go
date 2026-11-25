package client

import (
	"net/url"
	"strconv"
)

// cyberscorePath is the score of the organization of the X-Organization-ID header; it belongs to
// no board.
const cyberscorePath = kazanPath + "/cyberscore"

const cyberscoreChecksPath = cyberscorePath + "/checks"

func cyberscoreCheckPath(id string) string {
	return cyberscoreChecksPath + "/" + url.PathEscape(id)
}

// GetCyberscore reads the live score of the organization.
func (c *Client) GetCyberscore() (Cyberscore, error) {
	body, err := c.Get(cyberscorePath)
	if err != nil {
		return Cyberscore{}, err
	}
	return parseData[Cyberscore](body, "the cyberscore")
}

// GetCyberscoreTrend reads one score per day for the last days days, oldest first; 0 sends no
// days and takes the API's default window.
func (c *Client) GetCyberscoreTrend(days int) ([]CyberscoreTrendPoint, error) {
	query := url.Values{}
	if days != 0 {
		query.Set("days", strconv.Itoa(days))
	}
	body, err := c.Get(withQuery(cyberscorePath+"/trend", query))
	if err != nil {
		return nil, err
	}
	return parseData[[]CyberscoreTrendPoint](body, "the cyberscore trend")
}

// ListCyberscoreChecks reads the checks of the organization in the API's order, only those of
// status when it is not empty.
func (c *Client) ListCyberscoreChecks(status string) ([]CyberscoreCheck, error) {
	query := url.Values{}
	if status != "" {
		query.Set("status", status)
	}
	body, err := c.Get(withQuery(cyberscoreChecksPath, query))
	if err != nil {
		return nil, err
	}
	return parseData[[]CyberscoreCheck](body, "the cyberscore checks")
}

// CreateCyberscoreCheck creates a check and returns it.
func (c *Client) CreateCyberscoreCheck(req CyberscoreCheckRequest) (CyberscoreCheck, error) {
	body, err := c.Post(cyberscoreChecksPath, req)
	if err != nil {
		return CyberscoreCheck{}, err
	}
	return parseData[CyberscoreCheck](body, "the created check")
}

// UpdateCyberscoreCheck replaces every field of check id with req and returns it.
func (c *Client) UpdateCyberscoreCheck(id string, req CyberscoreCheckRequest) (CyberscoreCheck, error) {
	body, err := c.Put(cyberscoreCheckPath(id), req)
	if err != nil {
		return CyberscoreCheck{}, err
	}
	return parseData[CyberscoreCheck](body, "the updated check")
}

// SetCyberscoreCheckOutcome records the outcome of check id and returns it with the status the
// API derived from that outcome.
func (c *Client) SetCyberscoreCheckOutcome(id, outcome string) (CyberscoreCheck, error) {
	body, err := c.Put(cyberscoreCheckPath(id)+"/outcome", CyberscoreCheckOutcomeRequest{Outcome: outcome})
	if err != nil {
		return CyberscoreCheck{}, err
	}
	return parseData[CyberscoreCheck](body, "check "+id)
}

// SetCyberscoreCheckStatus sets the status of check id, and nothing else, and returns it.
func (c *Client) SetCyberscoreCheckStatus(id, status string) (CyberscoreCheck, error) {
	body, err := c.Put(cyberscoreCheckPath(id)+"/status", CyberscoreCheckStatusRequest{Status: status})
	if err != nil {
		return CyberscoreCheck{}, err
	}
	return parseData[CyberscoreCheck](body, "check "+id)
}

// DeleteCyberscoreCheck deletes check id.
func (c *Client) DeleteCyberscoreCheck(id string) error {
	_, err := c.Delete(cyberscoreCheckPath(id))
	return err
}
