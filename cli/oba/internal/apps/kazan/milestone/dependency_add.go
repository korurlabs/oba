package milestone

import (
	"errors"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// errNoDependency refuses depend and undepend without --on, before any call.
var errNoDependency = errors.New("--on is required: the milestone it depends on, by id or name")

// Dependency is the result of depend and undepend: the dependent as the API returns it and the
// milestone --on named.
type Dependency struct {
	Dependent client.Milestone
	On        client.Milestone
}

// Depend records that the milestone arg names depends on the one on names. The request goes to
// the dependent's own board, which needs ADMIN there.
func Depend(c *client.Client, boardID, arg, on string) (Dependency, error) {
	return changeDependency(c, boardID, arg, on, c.AddMilestoneDependency)
}

// changeDependency resolves both milestones and sends the change. The dependency must be related
// to the dependent's board, so it is looked up among the milestones reachable from that board.
func changeDependency(c *client.Client, boardID, arg, on string,
	send func(boardID, id, dependsOn string) (client.Milestone, error)) (Dependency, error) {
	if strings.TrimSpace(on) == "" {
		return Dependency{}, errNoDependency
	}
	m, err := Resolve(c, boardID, arg)
	if err != nil {
		return Dependency{}, err
	}
	dep, err := Resolve(c, owner(m, boardID), on)
	if err != nil {
		return Dependency{}, err
	}
	if strings.EqualFold(dep.ID, m.ID) {
		return Dependency{}, errors.New("a milestone cannot depend on itself")
	}
	updated, err := send(owner(m, boardID), m.ID, dep.ID)
	if err != nil {
		return Dependency{}, err
	}
	return Dependency{Dependent: updated, On: dep}, nil
}

// Conflict returns the dependency of the result on On when it is a conflict: the dependent
// starts before On is due.
func (d Dependency) Conflict() (client.MilestoneDependency, bool) {
	for _, dep := range d.Dependent.DependsOn {
		if dep.Conflict && strings.EqualFold(dep.MilestoneID, d.On.ID) {
			return dep, true
		}
	}
	return client.MilestoneDependency{}, false
}
