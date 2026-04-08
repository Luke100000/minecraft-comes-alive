# NeoForge 26.1.1 Upgrade Primer

This worktree starts from MCA `1.21.1` and targets the NeoForge 26.1.1 line used by the Herobrine branch.

Use both NeoForged primer sources:

- Rendered primer index: https://docs.neoforged.net/primer/docs/
- Upstream primer repo: https://github.com/neoforged/.github/tree/main/primers
- Direct 26.1 primer source: https://raw.githubusercontent.com/neoforged/.github/refs/heads/main/primers/26.1/index.md

Relevant migration chain from MCA `1.21.1` to the 26.1 line:

- `1.21.1 -> 1.21.2/3`
- `1.21.2/3 -> 1.21.4`
- `1.21.4 -> 1.21.5`
- `1.21.5 -> 1.21.6`
- `1.21.6 -> 1.21.7`
- `1.21.7 -> 1.21.8`
- `1.21.8 -> 1.21.9`
- `1.21.9 -> 1.21.10`
- `1.21.10 -> 1.21.11`
- `1.21.11 -> 26.1`

Primary links to keep open during the port:

- https://docs.neoforged.net/primer/docs/1.21.2/
- https://docs.neoforged.net/primer/docs/1.21.4/
- https://docs.neoforged.net/primer/docs/1.21.5/
- https://docs.neoforged.net/primer/docs/1.21.6/
- https://docs.neoforged.net/primer/docs/1.21.7/
- https://docs.neoforged.net/primer/docs/1.21.8/
- https://docs.neoforged.net/primer/docs/1.21.9/
- https://docs.neoforged.net/primer/docs/1.21.10/
- https://docs.neoforged.net/primer/docs/1.21.11/
- https://raw.githubusercontent.com/neoforged/.github/refs/heads/main/primers/26.1/index.md

High-signal items from the upstream `26.1` primer:

- Java moves from 21 to 25. Tooling and IDE support need to be updated before expecting a clean port.
- Vanilla returns to deobfuscated value types, so mapping-sensitive code and generated names need another pass.
- Loot type registration is rewritten around direct `MapCodec` registration instead of wrapper `*Type` holders.
- Validation is overhauled around `Validatable`, `ValidationContext`, and `ProblemReporter`.
- Villager trades become a data-driven registry, which is directly relevant to MCA because villager interaction and trade systems are core gameplay.

Notes for this repo:

- The upstream primer docs site currently exposes the `1.21.x` chain, while the newer `1.21.11 -> 26.1` primer lives in the `neoforged/.github` repository.
- The Herobrine branch currently targets `neo_version=26.1.1.11-beta`, so this worktree should treat the `26.1` primer as the final migration step after the `1.21.10 -> 1.21.11` primer.
