# Nighttime shelter distribution

Status: draft for review. Capacity distribution is not implemented. The user
selected a maximum of 30 additional walking-route blocks to prefer a house with
space. Unregistered house membership and admission accounting require engineering
verification before the implementation plan is finalized.

## Intended outcome

Incoming homeless villagers should prefer the nearest reachable house with space,
instead of repeatedly gathering around the nearest beds. A house's normal capacity
target is its total number of beds plus five villagers. Immediate shelter takes
priority when danger is detected or alternatives require excessive travel.

Villagers already on suitable indoor floor stay there. Capacity is an admission
preference, not a reason to evict villagers, prevent bed owners returning, or leave
villagers outside. Temporary shelter does not change HOME or claim a bed ticket.
The user's saved world remains inspection evidence only; all testing uses
disposable worlds.

## Current behavior and ownership

`SeekIndoorShelterTask` runs in the homeless REST package. It searches HOME POIs
within 48 blocks, takes the nearest five bed anchors, and selects reachable indoor
floor destinations. It excludes bed surfaces and reuses `EnterBuildingTask` floor
validation. Navigation currently chooses a reachable target from a set of valid
floor candidates, rather than relying on a single random sample.

`LocalInsideBrownianWalk` currently delegates to vanilla's local indoor wandering
and applies an existing retry gate. It does not find shelter from outdoors or
distribute villagers between houses. Preserve its local-wandering responsibility,
retry cadence, and navigation ownership; destination selection is being corrected
in the parallel work described below.

HOME acquisition remains separate from shelter selection. Existing panic, fleeing,
raid hiding, and normal bed-return behaviors retain control of their destinations.
Capacity applies only to ordinary homeless REST shelter selection.

## Coordination with indoor-targeting work

The active chat [Backport 1.21.1 pathfinding optim (2)](thread://01a10bd3-3dd2-77e1-995d-79adce443bd9?hostId=local)
is working on local indoor destination selection. Its investigation reports that
vanilla Brownian wandering can select a support block beneath a bed and navigation
can raise that destination onto the bed. The earlier regression checked the target
and the block below it, which did not cover a bed above the selected support block.

That work is correcting selection to use actual standing positions with support
and destination clearance, exclude bed surfaces, and test real movement around
beds and leaves, including villagers starting on a bed. These changes are in
progress, not a completed verification result.

The distribution change must build on the verified indoor-targeting correction.
Review its final floor-selection API and movement tests before implementation;
reuse the owning floor predicate where its semantics fit. Do not introduce a
second indoor selector or preserve the faulty vanilla selection just to keep this
spec's former description unchanged. Capacity distribution cannot compensate for
local wandering that continues to converge on a bed.

## Selection policy

1. Keep the existing early exit for sleeping villagers, villagers with HOME, and
   villagers already standing on suitable indoor floor.
2. Find nearby bed-bearing shelter candidates within the existing search radius.
   Group bed anchors by house before applying the candidate/pathfinding budget;
   five beds in one house must not conceal another house.
3. Find reachable clear indoor floor destinations using the existing floor checks
   and navigation. Preserve the nearest reachable candidate as an overflow option.
4. In normal conditions, prefer the nearest house below its capacity target if
   its reachable route is at most 30 walking blocks longer than the route to the
   nearest reachable shelter, regardless of that nearest shelter's capacity.
5. Use the nearest reachable overflow option when no suitable alternative exists,
   the alternative requires excessive travel, or the villager's existing threat
   information indicates danger. Reachability still applies during overflow.
6. Publish the selected WALK_TARGET through the existing movement owner. Once
   sheltered, leave subsequent local wandering to Brownian behavior.

Here, "nearest" starts with spatial ordering for candidate discovery. Decisions
about excessive travel must consider the route returned by navigation: a nearby
house can require a long detour. Do not describe a path as safe merely because it
is reachable or because the villager is not currently panicking.

The 30-block allowance is additional route length, not a new search radius or
maximum total distance. A route of 38 blocks is acceptable when the nearest
reachable shelter is 8 route blocks away; a route longer than 38 is not. Detected
danger takes precedence over this allowance and permits immediate overflow into
the nearest reachable shelter. Derive route length from path segment distances
rather than assuming node count equals distance.

## House identity

Use MCA's canonical logical building identity, scoped to its village and dimension,
for registered houses. Rooms and floors belonging to that building share one
capacity pool. Reuse the existing structure/floor geometry owner to resolve bed
heads, occupant positions, HOME positions, and walking destinations; do not create
another persisted house map.

Unregistered houses are a supported case: the existing shelter task handles them,
and the inspected save has auto-scanning disabled and incomplete registered room
geometry. A radius around a bed is not reliable house identity, especially for
neighboring buildings or multiple floors.

Before claiming distribution works for these houses, establish whether the
existing geometry scanner can provide bounded, read-only house membership without
registration or expensive repeated full scans. If membership is unknown, preserve
reachable shelter access rather than enforce a guessed capacity. This conservative
behavior permits crowding and must be reported as a limitation, not a completed
solution for unregistered houses.

## Capacity and admission accounting

Count compatible bed heads once across the whole house, regardless of whether a
bed is occupied. Normal capacity is `bedCount + 5`.

Account for three groups without double-counting a villager:

- Bed owners with a valid HOME in this house, including those currently away.
- Other villagers physically inside this house, including sleeping occupants.
- Other villagers with an active walking destination into this house.

Reserve room for returning bed owners. Their presence inside or their incoming
destination must not consume a second place. Admission counts concern villagers;
players and monsters do not consume places. Counting children as occupants is the
proposed default and should be covered by a test.

Derive occupancy and incoming intent from existing authoritative assignments,
entity positions, and WALK_TARGET state. Use unique entity identities where
available. Determine how existing POI claims represent unloaded or vanilla bed
owners before assuming MCA's resident list contains every owner.

Evaluate admission and publish its destination on the server thread. Later
selections must observe earlier incoming WALK_TARGETs. A changed or cleared target,
death, or departure must stop consuming an incoming place without a parallel
reservation counter. Include the boundary case of incoming entities outside the
local occupant-query area when defining the query scope.

## Safety precedence

Capacity does not constrain PANIC, fleeing, or emergency hiding. Preserve their
existing behavior packages; do not add shelter selection that competes with escape.
For normal REST selection, use existing threat memories and lifecycle rules rather
than introduce a second monster scanner. Nearby detected danger permits overflow
without requiring the PANIC activity to have started already.

When every available house is full, the next house is unreachable, or the next
route is too long, prefer reachable shelter over waiting outside for capacity.
When no shelter is reachable, retain existing village-seeking behavior; do not
invent teleportation or another locomotion fallback.

## Engineering checks before implementation

1. **Indoor-targeting dependency:** inspect the parallel agent's completed change
   and actual movement regressions. Integrate with its final floor checks without
   overlapping edits while that work is active.
2. **Unknown house geometry:** support ordinary unregistered village houses using
   proven read-only geometry where possible. If existing geometry cannot provide
   reliable membership at bounded cost, report that limitation before narrowing
   delivery to registered buildings. Preserve shelter access when membership is
   unknown; do not ask the user to register houses to make shelter movement work.
3. **Accounting sources:** verify bed-owner information for unloaded and vanilla
   villagers, and ensure the incoming query cannot omit distant active arrivals.
4. **Route measurement:** verify path-distance calculation and test the inclusive
   30-extra-block boundary. Keep this as a local policy constant unless a concrete
   requirement justifies a configuration option.

## Verification

Use focused GameTests for world and entity behavior, and JUnit only for pure
admission logic if it can be isolated without artificial wrappers.

| Scenario | Required observation |
| --- | --- |
| Nearest house below capacity | Incoming villager selects reachable floor there. |
| Nearest house full; nearby alternative available | Incoming villager selects the alternative. |
| Several nearby beds in the full house | Another house is still considered. |
| Consecutive arrivals competing for the last place | Later arrival observes the earlier walking target. |
| Returning bed owner already reserved | Owner is not counted twice; guests preserve the owner's place. |
| Multiple registered rooms/floors | Beds and occupants use the same logical house capacity. |
| Child or sleeping occupant | Occupant contributes once. |
| Cleared/changed walking target or removed villager | Old incoming intent no longer consumes capacity. |
| Alternative unreachable or excessively distant | Nearest reachable full house permits overflow. |
| Alternative exactly 30 route blocks farther | Under-capacity alternative is eligible. |
| Alternative more than 30 route blocks farther | Nearest reachable full house permits overflow. |
| Spatially close alternative requiring a long detour | Route distance, not straight-line distance, controls overflow. |
| Detected threat before PANIC begins | Ordinary shelter admission permits overflow. |
| PANIC, fleeing, or emergency hiding | Existing emergency movement is not blocked by capacity. |
| Villager already sheltered | Capacity does not publish a relocation target. |
| Neighboring unregistered houses | Supported geometry distinguishes them, or explicit unknown-geometry behavior permits shelter. |
| Furnished-room movement after arrival | Local wandering does not move villagers back onto beds; include starting on a bed. |
| All shelter choices | No bed-surface destination or new HOME/ticket claim. |

Run new regression cases before and after the change, then both loader builds.
Use a disposable client village to observe nighttime arrivals, overflow, and
multi-floor behavior. Builds and destination-selection tests alone do not prove
actual crowding or escape behavior. Review the final diff for duplicate state and
unnecessary abstractions.

## Scope and delivery

This draft changes no runtime behavior and makes no new verification claim.
Implementation should remain in shared gameplay/geometry owners, with loader code
limited to existing test registration seams. Keep discovery/pathfinding bounded
and use the existing staggered shelter cadence. Complete the engineering checks and
review this spec before writing the implementation plan.
