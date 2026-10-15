# Native flowing-water transit — Minecraft1.21.1

The Builder Fisher99 capture reproduces a worker held against a north bank while
its next native checkpoint lies south, against the current. Restoring the actual
scene and worker with only MCA's movement sink and vanilla Swim still fails the
80-tick journey. Builder scheduling, leases and access construction are absent.

`LivingEntity.travel` uses0.02 water acceleration with the default zero
`WATER_MOVEMENT_EFFICIENCY`. Native AI forward input at the captured walking
speed is about0.2695, giving about0.00539 forward acceleration. The opposing
`Entity` water-current scale is0.014. Native bank collision removes the opposing
velocity; repeating the same route cannot overcome that force. Increasing only
the native water-movement attribute in the captured test allows physical transit.

The correction adds `Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0D` to MCA's existing
villager attribute supplier. Minecraft continues to own acceleration, current,
drag, buoyancy and collision. There is no custom velocity, steering, retry,
timer, water-escape task or Builder-specific movement behavior. This changes
MCA villagers' swimming capability generally; it is not conditional on a build.
The saved worker does not serialize an override of this attribute, so loading
that capture uses the corrected native supplier.

`AutonomousPathfindingGameTests#brainTraversesFlowingWaterInsteadOfStayingAgainstBank`
uses the existing real Brain producer/sink and cleanup fixture. It requests one
LEVEL4-to-LEVEL3 southbound water cell at0.55 walking speed, after asserting the
northward current and airborne pose. The unchanged80-tick reaction window and
physical-motion assertion preserve the intended break. The test failed before
the correction and passed after it; common JUnit was actually rerun afterward.

Source comparison:

- Minecraft1.21.1: `LivingEntity.travel`, `Entity` fluid pushing, `Mob.setSpeed`,
  `MoveControl` and `Swim` establish the physical contract.
- MCA1.21.1: `VillagerEntityMCA.createAttributes`, `MCAMoveControl` and
  `WanderOrTeleportToTargetTask` retain their existing ownership.
- Minecraft26.3 retains the same0.02 acceleration,0.014 current scale and native
  water-efficiency interpolation. Its liquid release from `MoveControl.JUMPING`
  is a separate change, not evidence that this current stall is resolved there.
  MCA26.3 has no water-efficiency override. Both26.3 trees were read only.
- MineColonies owns water escape in its advanced navigator/float goal; Baritone
  owns player input and liquid movement costs. Those different locomotion owners
  are comparisons, not implementations to copy into Builders.

Evidence is under the Builder checkout's
`.superpowers/sdd/2026-10-13-121-vanilla-house-matrix/`: native RED/GREEN logs
`mca-swimming-native-red.log` and `mca-swimming-native-green.log`, and fixed
review patch `mca-swimming-reviewed.patch` (SHA256
`BD251FE94583C4F31FA20CBF3D2E38535BD0B6F6910724AA5D1DB07242193090`).
The four review lenses retain native delegation, no new state, existing fixture
cleanup, server-thread mutation and no added tick-time computation.

Both1.21.1 artifacts built;587 common JUnit tests and1 Fabric test pass. The
eight-test autonomous class run passed seven tests, including water transit,
but failed the vine-only doorway. That doorway passes in isolation on the same
source (`mca-vine-isolated-with-swimming.log`); class interaction/flakiness remains
open. Builder955 ordinary/17 Minecraft-aware tests and1.21.1 Fabric compile pass
against immutable rebuilt artifacts. Saved-actor/native controls and fresh full
Fisher acceptance are separate pending gates. Do not describe the full suite or
full house as accepted from this focused correction. Other MCA work is preserved.

## Publication verification, 15 October 2026

With the vine/Spanish-moss tests and unrelated performance work set aside, the
complete six-test `AutonomousPathfindingGameTests` class passed, including
`brainTraversesFlowingWaterInsteadOfStayingAgainstBank`. The selected source also
passed 589 common JUnit tests and both loader builds. The combined navigation
verification is recorded in `2026-10-14-121-nearby-native-detours.md`; it covers
104 required GameTests across six focused classes. The historical vine-suite
failure above remains a separate investigation, rather than a swimming failure
or a claimed fix.
