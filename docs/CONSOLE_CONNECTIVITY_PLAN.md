# Console Connectivity Plan (Early Prep)

## Goal

Support mixed PC and console players with a safe, maintainable connection path before public launch.

Because ATM11 is a Java modpack, direct native console clients cannot join the modded server without a protocol bridge. The practical path is Bedrock-to-Java bridging for supported gameplay flows.

## Scope and non-goals

- In scope: Join flow, account identity policy, moderation parity, and operational readiness for console players.
- In scope: Early private testing and launch guardrails.
- Out of scope (for launch): Full parity with every Java-mod interaction and every UI element from ATM11 mods.

## Proposed architecture

1. Keep the ATM11 Java server as the authoritative gameplay server.
2. Add a Bedrock bridge layer (for example Geyser) in front of Java access.
3. Add account linking and identity control (for example Floodgate or existing auth strategy) so moderation and profiles stay consistent.
4. Route both Java and Bedrock players into the same ServerCore-backed systems where behavior is compatible.

## Identity and policy decisions

- Define one canonical player identity key for stats, sanctions, cosmetics, and audit logs.
- Publish a clear account-link policy before beta:
  - What identity is displayed in-game and in server logs.
  - How linked/unlinked states behave.
  - What staff can and cannot merge.
- Ensure commands and moderation actions target linked identity, not protocol-specific temporary names.

## Gameplay compatibility strategy

- Label gameplay flows as:
  - Compatible: Works for both Java and console players.
  - Limited: Playable with known UX/performance caveats.
  - Java-only: Requires Java mod capabilities not feasible on console bridge.
- For launch, prioritize compatibility for:
  - Join and onboarding
  - Hub navigation and portal use
  - Core social play
  - Safety and moderation flows

## Security and abuse controls

- Require explicit allowlist or staged beta access for early console testing.
- Add protocol-aware rate limits for login bursts and malformed packets.
- Expand audit event metadata to include protocol type (Java or Bedrock) and linked identity status.
- Validate ban/mute/notes behavior across linked and unlinked edge cases.

## Operations checklist (pre-beta)

- Create version-pinned configs for bridge components.
- Add runbook entries:
  - Start-up order
  - Health checks
  - Safe rollback if bridge fails
- Add dashboard signals:
  - Java player count
  - Bedrock player count
  - Failed link/auth attempts
  - Join failures by protocol

## Private test milestones

1. Milestone A: Connectivity smoke
   - Console player reaches server and completes basic spawn flow.
   - Staff can identify and moderate the player reliably.
2. Milestone B: Core loop validation
   - Portal travel and basic progression actions are stable under mixed load.
   - No identity duplication in stats or audit records.
3. Milestone C: Load and failure drills
   - Mixed Java/Bedrock join bursts
   - Bridge restart and reconnect behavior
   - Moderation actions during active mixed sessions

## Release gates

Do not advertise public console support until all of the following are true:

- Mixed-traffic private tests pass.
- Identity linking policy is documented and staff-trained.
- Known Java-only limitations are published clearly.
- Rollback plan is tested and documented.

## Immediate next tasks

- Add bridge stack decision and version pinning to platform docs.
- Extend private test plan with console-specific cases.
- Add protocol metadata requirement to audit/event tracking backlog.
- Prepare staff-facing moderation and support playbook for linked identities.
