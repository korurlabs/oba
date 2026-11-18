package client

import "net/url"

// Comment is one comment of a ticket as the comment endpoints return it
// (CommentDetailResponse).
type Comment struct {
	ID              string `json:"id"`
	Content         string `json:"content"`
	TicketID        string `json:"ticketId"`
	AuthorID        string `json:"authorId"`
	AuthorEmail     string `json:"authorEmail"`
	AuthorFirstName string `json:"authorFirstName"`
	AuthorLastName  string `json:"authorLastName"`
	CreatedAt       string `json:"createdAt"`
	UpdatedAt       string `json:"updatedAt"`
}

// CommentRequest is the body of a new or changed comment (CommentCreateRequest and
// CommentUpdateRequest, which hold the same one field).
type CommentRequest struct {
	Content string `json:"content"`
}

// commentsPath is the comment collection of ticket ticketID on board boardID. The API checks
// that the ticket is on that board.
func commentsPath(boardID, ticketID string) string {
	return boardPath(boardID) + "/tickets/" + url.PathEscape(ticketID) + "/comments"
}

func commentPath(boardID, ticketID, commentID string) string {
	return commentsPath(boardID, ticketID) + "/" + url.PathEscape(commentID)
}

// ListComments reads the comments of a ticket. The API answers with one plain list, not pages.
func (c *Client) ListComments(boardID, ticketID string) ([]Comment, error) {
	body, err := c.Get(commentsPath(boardID, ticketID))
	if err != nil {
		return nil, err
	}
	return parseData[[]Comment](body, "the comments")
}

// AddComment adds a comment to a ticket.
func (c *Client) AddComment(boardID, ticketID, content string) (Comment, error) {
	body, err := c.Post(commentsPath(boardID, ticketID), CommentRequest{Content: content})
	if err != nil {
		return Comment{}, err
	}
	return parseData[Comment](body, "the added comment")
}

// UpdateComment replaces the content of a comment.
func (c *Client) UpdateComment(boardID, ticketID, commentID, content string) (Comment, error) {
	body, err := c.Put(commentPath(boardID, ticketID, commentID), CommentRequest{Content: content})
	if err != nil {
		return Comment{}, err
	}
	return parseData[Comment](body, "the updated comment")
}

// DeleteComment deletes a comment.
func (c *Client) DeleteComment(boardID, ticketID, commentID string) error {
	_, err := c.Delete(commentPath(boardID, ticketID, commentID))
	return err
}
