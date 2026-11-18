package delivery

import (
	"context"
	"errors"
)

// Poll is one round of the watcher: it asks Kaya for the current release and builds a newer one
// for the next session, without switching.
func (d *Deliverer) Poll(ctx context.Context) error {
	if d.Source == nil {
		return errors.New("no machine token")
	}
	c := d.Checkout
	unlock, err := c.Lock(ctx)
	if err != nil {
		return err
	}
	defer unlock()
	state, err := c.LoadState()
	if err != nil {
		return err
	}
	if state.RequestRemote == "" {
		return errors.New("the checkout was never synced")
	}
	var out Outcome
	d.contact(ctx, &state, &out, d.organization(state))
	if out.Offline != nil && state.CheckedAt == "" {
		return out.Offline
	}
	var problems []error
	if out.Offline != nil {
		problems = append(problems, out.Offline)
	}
	for _, warning := range out.Warnings {
		problems = append(problems, errors.New(warning))
	}
	if err := c.SaveState(state); err != nil {
		problems = append(problems, err)
	}
	return errors.Join(problems...)
}
