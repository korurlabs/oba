import {
  CHECK_SORT_ORDER_MIN,
  CheckOutcome,
  CheckStatus,
  type Check,
  type CheckFormInput,
  type CheckFormValues,
  type CheckRequest,
} from "../schemas";

/** Weight a new check starts with; the admin sets the real one in the dialog. */
export const CHECK_DEFAULT_WEIGHT = 10;

/**
 * The status an outcome implies, as the server derives it: not performed is
 * not started, not applicable stays not applicable, any result is done. The
 * check dialog prefills the status with it when the outcome changes; the
 * user can still pick another one, which the server then keeps.
 */
export function deriveCheckStatus(outcome: CheckOutcome): CheckStatus {
  switch (outcome) {
    case CheckOutcome.TODO:
      return CheckStatus.NOT_STARTED;
    case CheckOutcome.NOT_APPLICABLE:
      return CheckStatus.NOT_APPLICABLE;
    case CheckOutcome.PASS:
    case CheckOutcome.PARTIAL:
    case CheckOutcome.FAIL:
    case CheckOutcome.NO_LICENSE:
      return CheckStatus.DONE;
  }
}

/** Form values of the create dialog, or of the edit dialog for `check`. */
export function checkFormDefaults(check?: Check): CheckFormInput {
  if (!check) {
    return {
      name: "",
      description: "",
      expected: "",
      weight: CHECK_DEFAULT_WEIGHT,
      sortOrder: CHECK_SORT_ORDER_MIN,
      category: null,
      outcome: CheckOutcome.TODO,
      status: deriveCheckStatus(CheckOutcome.TODO),
    };
  }
  return {
    name: check.name,
    description: check.description ?? "",
    expected: check.expected,
    weight: check.weight,
    sortOrder: check.sortOrder,
    category: check.category,
    outcome: check.outcome,
    status: check.status,
  };
}

/**
 * The request body of validated form values. An empty description is sent
 * as null, which clears it; outcome and status are always sent, so the
 * server stores what the dialog shows.
 */
export function checkFormToRequest(values: CheckFormValues): CheckRequest {
  return {
    name: values.name,
    description: values.description === "" ? null : values.description,
    expected: values.expected,
    weight: values.weight,
    sortOrder: values.sortOrder,
    category: values.category,
    outcome: values.outcome,
    status: values.status,
  };
}
