/**
 * Production gate, checked by the deploy-prod server itself (server side), so
 * that it holds even when no client-side hook runs.
 *
 * Part 2 of ADR 0007: the check against the change ticket (a GitHub issue
 * labelled release-approved by the release manager, for this exact version)
 * is added in part 3. Until then the gate FAILS CLOSED: every production
 * release is refused. Status and rollback do not go through the gate.
 */
export interface GateDecision {
  authorized: boolean;
  reason: string;
}

export async function checkProductionTicket(ticket: number | undefined, version: string): Promise<GateDecision> {
  if (ticket === undefined) {
    return { authorized: false, reason: "a production release needs a change ticket" };
  }
  return {
    authorized: false,
    reason:
      `ticket #${ticket} for ${version} cannot be verified yet: ` +
      "the production gate is not configured, so production releases are refused (fail closed)",
  };
}
