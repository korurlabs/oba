package client

import (
	"encoding/json"
	"fmt"
	"net/url"
	"strconv"
)

// Board is one Kazan board as GET /kaizen/boards lists it (BoardListWithChildrenResponse) and
// GET /kaizen/boards/{id} returns it (BoardListResponse). ChildBoards is set by the list with
// includeChildren only, at every depth of the board tree. Raw keeps the board as the API sent
// it, child boards included, so that --json can print a filtered tree without losing fields.
type Board struct {
	ID          string          `json:"id"`
	Name        string          `json:"name"`
	Description string          `json:"description"`
	Prefix      string          `json:"prefix"`
	Slug        string          `json:"slug"`
	Color       *string         `json:"color"`
	Icon        *string         `json:"icon"`
	ChildBoards []Board         `json:"childBoards"`
	Raw         json.RawMessage `json:"-"`
}

// UnmarshalJSON reads the typed fields and keeps the whole object in Raw.
func (b *Board) UnmarshalJSON(data []byte) error {
	type plain Board
	var typed plain
	if err := json.Unmarshal(data, &typed); err != nil {
		return err
	}
	typed.Raw = append(json.RawMessage(nil), data...)
	*b = Board(typed)
	return nil
}

// BoardMemberRequest is one initial member of a new board (BoardMemberAddRequest).
type BoardMemberRequest struct {
	UserID string `json:"userId"`
	Role   string `json:"role"`
}

// BoardCreateRequest is the body of POST /kaizen/boards.
type BoardCreateRequest struct {
	Name          string               `json:"name"`
	Description   string               `json:"description,omitempty"`
	Prefix        string               `json:"prefix"`
	Color         *string              `json:"color,omitempty"`
	Icon          *string              `json:"icon,omitempty"`
	ParentBoardID *string              `json:"parentBoardId,omitempty"`
	Members       []BoardMemberRequest `json:"members,omitempty"`
}

// BoardUpdateRequest is the body of PUT /kaizen/boards/{id}; a nil field is left unchanged.
type BoardUpdateRequest struct {
	Name        *string `json:"name,omitempty"`
	Description *string `json:"description,omitempty"`
	Prefix      *string `json:"prefix,omitempty"`
	Color       *string `json:"color,omitempty"`
	Icon        *string `json:"icon,omitempty"`
}

// boardChildrenRequest is the BoardAddChildrenRequest body.
type boardChildrenRequest struct {
	ChildBoardIDs []string `json:"childBoardIds"`
}

// ListBoards reads every board of the organization the user is a member of. With
// includeChildren, child boards are nested under their parent and not listed on their own;
// without it every board is listed flat, without children.
func (c *Client) ListBoards(includeChildren bool) ([]Board, error) {
	query := url.Values{}
	query.Set("includeChildren", strconv.FormatBool(includeChildren))
	return ListPages[Board](c, boardsPath, query)
}

// GetBoard reads board id. A board the user is not a member of reads as not found.
func (c *Client) GetBoard(id string) (Board, error) {
	body, err := c.Get(boardPath(id))
	if err != nil {
		return Board{}, err
	}
	return parseBoard(body, fmt.Sprintf("failed to read board %s", id))
}

// CreateBoard creates a board and returns it.
func (c *Client) CreateBoard(req BoardCreateRequest) (Board, error) {
	body, err := c.Post(boardsPath, req)
	if err != nil {
		return Board{}, err
	}
	return parseBoard(body, "the board was created, but the response could not be read")
}

// UpdateBoard changes the given fields of board id and returns the board.
func (c *Client) UpdateBoard(id string, req BoardUpdateRequest) (Board, error) {
	body, err := c.Put(boardPath(id), req)
	if err != nil {
		return Board{}, err
	}
	return parseBoard(body, fmt.Sprintf("board %s was updated, but the response could not be read", id))
}

// DeleteBoard deletes board id. query says where its tickets and child boards go
// (BoardDeleteRequest: ticketAction, targetBoardId, targetSprintId, childBoardAction,
// newParentBoardId); the API asks for them only when the board has tickets or children.
func (c *Client) DeleteBoard(id string, query url.Values) error {
	path := boardPath(id)
	if len(query) > 0 {
		path += "?" + query.Encode()
	}
	_, err := c.Delete(path)
	return err
}

// RestoreBoard restores the deleted board id.
func (c *Client) RestoreBoard(id string) error {
	_, err := c.Post(boardPath(id)+"/restore", nil)
	return err
}

// AddChildBoards makes the boards childIDs children of board id and returns the updated boards.
func (c *Client) AddChildBoards(id string, childIDs []string) ([]Board, error) {
	body, err := c.Put(boardPath(id)+"/children", boardChildrenRequest{ChildBoardIDs: childIDs})
	if err != nil {
		return nil, err
	}
	return parseBoards(body, "the child boards were added, but the response could not be read")
}

// ListRelatedBoards reads the boards related to board id in its board tree, the board itself
// left out. The API answers with one plain list, not pages.
func (c *Client) ListRelatedBoards(id string) ([]Board, error) {
	body, err := c.Get(boardPath(id) + "/related")
	if err != nil {
		return nil, err
	}
	return parseBoards(body, fmt.Sprintf("failed to read the boards related to %s", id))
}

func parseBoard(body []byte, failure string) (Board, error) {
	var resp APIResponse[Board]
	if err := json.Unmarshal(body, &resp); err != nil {
		return Board{}, fmt.Errorf("%s: %w", failure, err)
	}
	return resp.Data, nil
}

func parseBoards(body []byte, failure string) ([]Board, error) {
	var resp APIResponse[[]Board]
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("%s: %w", failure, err)
	}
	return resp.Data, nil
}
