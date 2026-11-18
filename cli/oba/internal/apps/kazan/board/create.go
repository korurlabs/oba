package board

import (
	"errors"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/shrine/organization"
)

// CreateInput is a new board. Color and Icon are nil when not given; Parent names the parent
// board by name, key prefix or id, empty for a top-level board; Members are
// "<email or userId>:<ROLE>" values.
type CreateInput struct {
	Name        string
	Description string
	Key         string
	Color       *string
	Icon        *string
	Parent      string
	Members     []string
}

// Create checks in, resolves its parent and members and creates the board. The checks run
// before any call.
func Create(c *client.Client, in CreateInput) (client.Board, error) {
	name, key := strings.TrimSpace(in.Name), strings.TrimSpace(in.Key)
	if name == "" || key == "" {
		return client.Board{}, errors.New("--name and --key are required")
	}
	specs, err := ParseMemberSpecs(in.Members)
	if err != nil {
		return client.Board{}, err
	}
	req := client.BoardCreateRequest{Name: name, Description: in.Description, Prefix: key, Color: in.Color, Icon: in.Icon}
	if strings.TrimSpace(in.Parent) != "" {
		parent, err := Resolve(c, in.Parent)
		if err != nil {
			return client.Board{}, err
		}
		req.ParentBoardID = &parent.ID
	}
	if len(specs) > 0 {
		refs := make([]string, len(specs))
		for i, s := range specs {
			refs[i] = s.User
		}
		ids, err := organization.ResolveMemberIDs(c, refs)
		if err != nil {
			return client.Board{}, err
		}
		for i, id := range ids {
			req.Members = append(req.Members, client.BoardMemberRequest{UserID: id, Role: specs[i].Role})
		}
	}
	return c.CreateBoard(req)
}
