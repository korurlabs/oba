package client

import (
	"encoding/json"
	"fmt"
	"net/url"
)

// BoardMember is one member of a board as GET /kazan/boards/{boardId}/members lists it
// (BoardMemberResponse). The member calls address a member by UserID.
type BoardMember struct {
	ID        string `json:"id"`
	UserID    string `json:"userId"`
	Email     string `json:"email"`
	FirstName string `json:"firstName"`
	LastName  string `json:"lastName"`
	Role      string `json:"role"`
	CreatedAt string `json:"createdAt"`
}

// boardMemberRoleRequest is the BoardMemberUpdateRequest body.
type boardMemberRoleRequest struct {
	Role string `json:"role"`
}

// boardMemberSpecialtiesRequest is the body that replaces a member's specialties.
type boardMemberSpecialtiesRequest struct {
	Specialties []string `json:"specialties"`
}

func boardMembersPath(boardID string) string {
	return boardPath(boardID) + "/members"
}

func boardMemberPath(boardID, userID string) string {
	return boardMembersPath(boardID) + "/" + url.PathEscape(userID)
}

// ListBoardMembers reads the members of board boardID. The API leaves out the members who are no
// longer active in the board's organization and answers with one plain list, not pages.
func (c *Client) ListBoardMembers(boardID string) ([]BoardMember, error) {
	body, err := c.Get(boardMembersPath(boardID))
	if err != nil {
		return nil, err
	}
	var resp APIResponse[[]BoardMember]
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("failed to read the members of board %s: %w", boardID, err)
	}
	return resp.Data, nil
}

// AddBoardMember adds a user to board boardID with a role and returns the new member.
func (c *Client) AddBoardMember(boardID string, req BoardMemberRequest) (BoardMember, error) {
	body, err := c.Post(boardMembersPath(boardID), req)
	if err != nil {
		return BoardMember{}, err
	}
	return parseBoardMember(body, "the member was added, but the response could not be read")
}

// ChangeBoardMemberRole gives the member userID of board boardID the role and returns the member.
func (c *Client) ChangeBoardMemberRole(boardID, userID, role string) (BoardMember, error) {
	body, err := c.Put(boardMemberPath(boardID, userID), boardMemberRoleRequest{Role: role})
	if err != nil {
		return BoardMember{}, err
	}
	return parseBoardMember(body, "the role was changed, but the response could not be read")
}

// SetBoardMemberSpecialties replaces the specialties of the member userID of board boardID.
func (c *Client) SetBoardMemberSpecialties(boardID, userID string, specialties []string) error {
	_, err := c.Put(boardMemberPath(boardID, userID)+"/specialties", boardMemberSpecialtiesRequest{Specialties: specialties})
	return err
}

// RemoveBoardMember removes the member userID from board boardID.
func (c *Client) RemoveBoardMember(boardID, userID string) error {
	_, err := c.Delete(boardMemberPath(boardID, userID))
	return err
}

func parseBoardMember(body []byte, failure string) (BoardMember, error) {
	var resp APIResponse[BoardMember]
	if err := json.Unmarshal(body, &resp); err != nil {
		return BoardMember{}, fmt.Errorf("%s: %w", failure, err)
	}
	return resp.Data, nil
}
