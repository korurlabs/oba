package client

import "net/url"

// Label is one label of a board as the label endpoints return it. Color is "#RRGGBB" or null.
type Label struct {
	ID    string  `json:"id"`
	Name  string  `json:"name"`
	Color *string `json:"color"`
}

// LabelCreateRequest is the body of POST /kazan/boards/{boardId}/labels.
type LabelCreateRequest struct {
	Name  string  `json:"name"`
	Color *string `json:"color,omitempty"`
}

// LabelUpdateRequest is the body of PUT /kazan/boards/{boardId}/labels/{labelId}; a nil field
// is left unchanged.
type LabelUpdateRequest struct {
	Name  *string `json:"name,omitempty"`
	Color *string `json:"color,omitempty"`
}

func labelsPath(boardID string) string {
	return boardPath(boardID) + "/labels"
}

func labelPath(boardID, labelID string) string {
	return labelsPath(boardID) + "/" + url.PathEscape(labelID)
}

// ListLabels reads the labels of a board. The API answers with one plain list, not pages.
func (c *Client) ListLabels(boardID string) ([]Label, error) {
	body, err := c.Get(labelsPath(boardID))
	if err != nil {
		return nil, err
	}
	return parseData[[]Label](body, "the labels")
}

// CreateLabel creates a label on a board.
func (c *Client) CreateLabel(boardID string, req LabelCreateRequest) (Label, error) {
	body, err := c.Post(labelsPath(boardID), req)
	if err != nil {
		return Label{}, err
	}
	return parseData[Label](body, "the created label")
}

// UpdateLabel changes the given fields of a label.
func (c *Client) UpdateLabel(boardID, labelID string, req LabelUpdateRequest) (Label, error) {
	body, err := c.Put(labelPath(boardID, labelID), req)
	if err != nil {
		return Label{}, err
	}
	return parseData[Label](body, "the updated label")
}

// DeleteLabel deletes a label of a board.
func (c *Client) DeleteLabel(boardID, labelID string) error {
	_, err := c.Delete(labelPath(boardID, labelID))
	return err
}
