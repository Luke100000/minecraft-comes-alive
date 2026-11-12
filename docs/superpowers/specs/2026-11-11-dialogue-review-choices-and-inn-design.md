# Review earlier dialogue choices and simplify the missing-inn reply

**Status:** Design agreed in conversation on 2026-11-11; implementation pending.
**Target:** MCA 1.21.1 common DialogueEvent and Talk UI, Fabric and NeoForge.

## Intent

Previous/Next should let the player understand how the current conversation developed. Earlier villager questions should show **all replies offered at that moment**, with the player's chosen answer clearly marked. History is read-only. Separately, the first-contact missing-inn response should stop asking an almost identical follow-up question.

## Player-visible behavior

- Previous shows the earlier villager line in parchment and the corresponding historical choices in the existing reply panel. Every original option appears in its original order, disabled. A chosen option gets a visible checkmark and highlight. Lines with no choices show their original Continue/Finish control as disabled when applicable.
- Next moves forward through these snapshots. Return to current restores the actual live line and its choices, including existing reveal progress. History browsing sends no packets and cannot change earlier answers, rewards, effects, or dialogue outcomes.
- Keep the existing 64-line, current-session-only history. Duplicate/retransmitted node packets add no duplicate entries. Pause/Topics/Resume preserve the snapshot, while ending, clearing, or replacing a session discards it. An ambient preview admitted into history has no fabricated choices.
- The first-contact `spent_night_failed` node offers the existing `no_inn_travel` and `no_inn_stay` replies directly after the villager explains there is no inn. Remove its intermediate `no_inn_followup` node and the extra line “Sorry I couldn't help more. What will you do?”. Keep the existing reply IDs, translations, outcomes, and inn-present route.

## Approach

**Chosen:** Replace the client-only speech history's bare lines with bounded snapshots of each accepted line, its server-offered choices, its advance control, and the choice the player selected once the server accepts the transition. Reuse the existing Previous/Next index and reply panel. Capture the selected choice through the existing click/response lifecycle with offer-token and session checks.

**Alternatives considered:** Reconstruct earlier choices by replaying event graph state on the server (adds protocol/state and risks changed conditions), or remember only the selected reply (fails the request to see every earlier option). Neither is needed.

For the inn case, move the two existing choices to the already-present failed-inn line. An additional translated line or special dialogue engine rule would complicate this small content fix.

## Preservation and acceptance

- Current-choice clicks remain server-authoritative; reviewing history exposes no actionable hitboxes, focus actions, or advancement packets. A rejected, stale, abandoned, or wrong-session choice never gains a historical checkmark.
- Capture original offered choice text and order as rendered in that session, without re-evaluating datapack conditions when navigating history. Review mode never displays the *current* node's replies under an *earlier* villager line.
- Preserve text reveal, scrolling/scissor, keyboard navigation, Talk menu, speech-position toggle, and both loaders. No new packet, persistent history, replay, or save migration.
- The inn-failure path needs only one villager answer before its two player replies, and both still reach their existing endings. Other first-contact paths remain as authored.
- Verify focused client presentation and history state tests, the relevant dialogue-content resource test, and a real GUI view at ordinary and narrow scales. Compilation alone does not establish visual acceptance.

## Implementation ownership

`ClientHandlerImpl.DialoguePresentation` owns client history and server-acknowledged choice selection. `InteractScreen` renders the reviewed option snapshots without clicks. `social/first.json` owns the inn conversation graph. Keep changes limited to those owners and existing focused tests, preserving concurrent edits in this worktree.
