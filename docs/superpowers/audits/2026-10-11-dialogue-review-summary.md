# English dialogue editorial review — 2026-10-11

## Coverage and disposition

Reviewed the frozen **5,605-entry** English dialogue inventory across the base dialogue pack and nine personality packs. Every entry was assigned once. The two independent review shards and the primary social-conversation review are preserved as adjacent JSONL files, with per-key `keep`/`rewrite` decisions and suggested replacements:

| Review | Reviewed | Keep proposals | Rewrite proposals |
| --- | ---: | ---: | ---: |
| A (base, crabby, flirty, gloomy) | 2,769 | 2,285 | 484 |
| B (gloomy, greedy, introverted, odd, playful, relaxed, sensitive) | 2,768 | 772 | 1,996 |
| Primary (new Chat/Joke/Story/first keys) | 68 | 53 | 15 |
| **Total** | **5,605** | **3,110** | **2,495** |

From the two review shards, **1,256** proposed replacements were incorporated in the ten locales: base 75, crabby 15, flirty 223, gloomy 105, greedy 146, introverted 102, odd 154, playful 171, relaxed 113 and sensitive 152. The primary review additionally revised 15 of its 68 reserved keys. These counts refer to review decisions and do not include separately authored Task 2–3 lines or later small wording fixes.

The remaining **1,224** shard rewrite proposals were not blindly merged: 72 were too clipped, 322 applied to an unused legacy biome Chat pool, 375 affected location facts that need gameplay-specific review, and 455 short proposals from review B lacked enough evidence to justify changing an otherwise concise line. **421** unreferenced old biome-Chat translations were removed across the ten English packs after checking the new event graph, Java references and personality language-pool lookup; those keys are excluded from the 1,256 revisions.

## Editorial decisions

- Preserve key identity, numbered pools and format placeholders for retained keys. Preserve personality distinctions and deliberate humor while reducing excessive narration, repetitive pet names, filler and misleading claims.
- Keep village-life Chat replies about village life; local scenery uses the separate local-area Ask conversation and biome ambient entries, with neutral wording when a villager has no home.
- Avoid asserting real events without gameplay evidence (for instance, a house was repaired, a merchant is coming, or the villager recently saw something). Keep positive/negative reactions consistent with the player choice that produced them.
- Do not rewrite the untouched non-English locales; this is an English-language authoring pass. The archived review suggestions are **not** all accepted edits.

## Verification and limitations

The shard merge checked one-to-one review coverage, conflicts against the frozen inventory, JSON keys and placeholder preservation. The full `:common:test :fabric:build :neoforge:build` Gradle command succeeded after the large corpus merge; final focused checks and `git diff --check` are documented in the task handoff. A full live-world GameTest covering real biome tags, home chunk loading, all dialogue personalities/ages, UI wrapping and every choice sequence was **not** performed. These cases still warrant manual 1.21.1 gameplay testing; in particular, deferred location-dialogue editorial suggestions may contain facts dependent on live locations.

The JSONL reports are review records, not a generated production dialogue system. No compatibility path for the obsolete biome condition schema is retained.
