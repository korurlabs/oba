package client

import (
	"encoding/json"
	"fmt"
	"net/url"
)

// Project is one project of a board as GET /kaizen/boards/{boardId}/projects lists it
// (ProjectListResponse) and the single-project calls return it (ProjectDetailResponse). A project
// has a name and a color only.
type Project struct {
	ID    string  `json:"id"`
	Name  string  `json:"name"`
	Color *string `json:"color"`
}

// ProjectCreateRequest is the body of POST /kaizen/boards/{boardId}/projects.
type ProjectCreateRequest struct {
	Name  string  `json:"name"`
	Color *string `json:"color,omitempty"`
}

// ProjectUpdateRequest is the body of PUT /kaizen/boards/{boardId}/projects/{id}; a nil field is
// left unchanged.
type ProjectUpdateRequest struct {
	Name  *string `json:"name,omitempty"`
	Color *string `json:"color,omitempty"`
}

func projectsPath(boardID string) string {
	return boardPath(boardID) + "/projects"
}

func projectPath(boardID, id string) string {
	return projectsPath(boardID) + "/" + url.PathEscape(id)
}

// ListProjects reads every project of board boardID. The API answers with one plain list, not
// pages.
func (c *Client) ListProjects(boardID string) ([]Project, error) {
	body, err := c.Get(projectsPath(boardID))
	if err != nil {
		return nil, err
	}
	var resp APIResponse[[]Project]
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("failed to read the projects of board %s: %w", boardID, err)
	}
	return resp.Data, nil
}

// GetProject reads project id of board boardID.
func (c *Client) GetProject(boardID, id string) (Project, error) {
	body, err := c.Get(projectPath(boardID, id))
	if err != nil {
		return Project{}, err
	}
	return parseProject(body, fmt.Sprintf("failed to read project %s", id))
}

// CreateProject creates a project on board boardID and returns it.
func (c *Client) CreateProject(boardID string, req ProjectCreateRequest) (Project, error) {
	body, err := c.Post(projectsPath(boardID), req)
	if err != nil {
		return Project{}, err
	}
	return parseProject(body, "the project was created, but the response could not be read")
}

// UpdateProject changes the given fields of project id of board boardID and returns it.
func (c *Client) UpdateProject(boardID, id string, req ProjectUpdateRequest) (Project, error) {
	body, err := c.Put(projectPath(boardID, id), req)
	if err != nil {
		return Project{}, err
	}
	return parseProject(body, fmt.Sprintf("project %s was updated, but the response could not be read", id))
}

// DeleteProject deletes project id of board boardID.
func (c *Client) DeleteProject(boardID, id string) error {
	_, err := c.Delete(projectPath(boardID, id))
	return err
}

func parseProject(body []byte, failure string) (Project, error) {
	var resp APIResponse[Project]
	if err := json.Unmarshal(body, &resp); err != nil {
		return Project{}, fmt.Errorf("%s: %w", failure, err)
	}
	return resp.Data, nil
}
