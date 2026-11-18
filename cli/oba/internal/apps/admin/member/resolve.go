package member

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/shrine/organization"
)

// Resolve reads the members of the client's organization and picks the one value names, as
// organization.FindMember does.
func Resolve(c *client.Client, orgName, value string) (client.Member, error) {
	members, err := c.ListMembers()
	if err != nil {
		return client.Member{}, err
	}
	return organization.FindMember(members, orgName, value)
}
