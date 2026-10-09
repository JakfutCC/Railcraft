# Anchor loading

Block anchors restore their configured areas before the server's world ticks and
AE2's tile-ready queue. A location index supplements Forge's saved tickets so
personal anchors can resume when their owner logs in. Existing block IDs, crafting
recipes and fuel item lists are unchanged. Visit existing personal anchors once
after upgrading to add them to the index; old Railcraft discarded their tickets
on restart.

In CubicChunks, an anchor holds a cuboid of 16x16x16 cubes, initially 3x3x3.
Without CubicChunks it holds ordinary columns, initially 3x3; Y controls are
disabled. The GUI controls each directional extent. Mini selects one cube or
column. The crowbar/sentinel connection selects an axis-aligned line, including
vertical lines in CC. Outline draws the selected bounds in the world.

World and Admin anchors keep dimensions loaded. Personal anchors stop five
minutes after logout. Passive anchors do not restore themselves at startup and
do not prevent an otherwise idle dimension from unloading. Player presence,
spawn retention, other mods' Forge or ticking cube tickets, and active World,
Personal or Admin anchors prevent eviction. Save-disabled dimensions are never
evicted. Cleanup uses Forge's normal save/unload events and flush path.

`/rcanchors` lists the player's indexed anchors, with coordinates and highlight
buttons. `/rcanchors all` is the operator view; an optional page number selects
another page. `/rcanchors evict <dimension>` is operator-only and retains the
same safety checks as automatic cleanup. Listing does not load the listed worlds.

With Navigator installed, the Railcraft anchors layer shows the owner's areas
as X/Z footprints, with the loaded Y range in each tooltip. These are not claims
or full-column loading requests. Snapshots refresh every two seconds, do not load
terrain, and show at most 128 anchors in the viewport; zoom in if the tooltip
reports truncation. The administrator list still provides all owners and dimensions.

## Server settings

All settings are in the `anchors` category of `railcraft.cfg`:

- `maximum.cubes`: per-anchor cube budget, default 4096. A vanilla column costs 16.
- `maximum.extent`: maximum per-side extension, default 32, hard ceiling 128.
- `owner.cube.budget`: combined active-anchor budget without rank integration.
- `serverutilities.rank.limits`: use SU's team rank budget, or the owner's rank
  for a player without a team. Each rank chunk is 16 cubes. Claimed chunks marked
  for loading consume the same budget, including across dimensions.
- `idle.dimension.seconds`: grace before passive-only dimensions unload, default
  300 seconds of server ticks.
- `idle.dimension.watchdog`: also inspect dimensions without Railcraft anchors;
  opt-in, default false. It never overrides another mod's loading tickets.

Forge/CC ticket limits still apply. Admin anchors bypass fuel and owner/configured
volume budgets, but retain a 32768-cube allocation safety ceiling and ticket limits.
Fuel duration uses the existing configured hours at a reference area of 144 cubes
(nine vanilla columns). Credit is consumed proportionally to the actual area and
retained when changing the area, breaking/replacing the anchor or reloading.
Changing the permitted fuel items, such as adding ender pearls to World anchors,
uses the existing `world.fuel`, `personal.fuel` and `passive.fuel` lists.

Rank integration is enabled by default. Anchors over the team's budget, and
anchors whose owner is unknown to SU, remain inactive. This can disable existing
anchors when upgrading; change the rank budget or explicitly disable the rank
integration if the server wants the fallback owner budget. The GUI reports refusals.
Range edits are debounced, and fuel slot layouts do not depend on client fuel config.
Previously active anchors restore first when the budget is contested. Budget-blocked
World/Personal anchors retry every ten seconds once the budget and owner state allow
it, even if their cube has unloaded. Editing an area's bounds briefly releases the
old area before applying the new one; this does not promise uninterrupted networks
while resizing.

CC requires the companion cube-ticket lifecycle fix: registration of the Forge
ticket accessor, tracking requested cubes and releasing them with the ticket.
Unpatched CC builds have a broken force-cube API; anchors refuse cube loading with
a clear status and log message when the companion API is absent. UniMixins is a
required dependency (already included in GTNH). The server-only Forge unload
boundary mixin rechecks players, saving and external tickets immediately before
an anchor-requested eviction; it preserves normal unload events for AE2 cleanup.

Claims remain ServerUtilities' two-dimensional ownership regions. This patch
does not convert SU claim-based loading, anchor carts or other mods' loaders to
cube-based loading. The dedicated anchor GUI/list and world outline do not
depend on a mapping mod.
