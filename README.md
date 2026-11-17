# ME Rebalancer

[![Build](https://github.com/neldridge/me-rebalancer/actions/workflows/build.yml/badge.svg)](https://github.com/neldridge/me-rebalancer/actions/workflows/build.yml)

A NeoForge addon that migrates existing items toward higher-priority AE2 storage while cells remain installed in their drives. Connect drawers with a normal storage bus, give that bus a higher priority than the drives, and configure the rebalancer.

**Development version: 0.3.2-dev.** Includes a filter GUI, live filter edits, Test Run previews, and destination labels. Builds do not automatically install into a modpack.

## Exact development target

| Component | Version |
| --- | --- |
| FTB Evolution 2 | 1.9.0 |
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.126 |
| AE2 | 26.1.13-beta |
| Functional Storage (optional destination) | 26.1.2-1.6.4 |
| Java development kit | 25 |

AE2 is pinned exactly because the integration reads its private mounted-inventory table through a small Mixin. Functional Storage is optional; other storage mounted on AE2 can participate.

## Configuration GUI

Right-click the block to open its menu.

- **64 ghost filters over four pages.** Pick up an inventory stack and click a filter to copy its exact item/component variant. The actual stack stays on the cursor. Right-click a filter to remove it.
- **Shift-click an inventory item** to add it to the first available filter without moving or consuming the stack.
- **Selected items / All items: ON** explicitly selects the transfer scope. An empty whitelist matches nothing. New blocks start with no filters and all-items mode off.
- **Start / Pause** controls transfers. The Start button is disabled until a filter or explicit all-items scope is selected.
- **Run once / Continuous** chooses whether to stop when settled or keep checking for useful moves.
- **Clear** removes every filter and disables all-items scope.
- Adding, removing, or clearing filters and changing scope apply immediately while preserving Running or Paused. The scan restarts with the updated selection; an empty selection matches nothing. A run-once operation may subsequently settle, while Continuous keeps monitoring.
- Status, lifetime items moved, and recovery buffer count update from the server. Hover over the counters to see exact large values.

The client displays 18 filters per page. Filter selection and transfer controls are validated on the server. Ghost slots cannot be picked up, swapped into inventory, cloned, thrown, or used for drag distribution.

When upgrading an original prototype block, saved item filters are imported. Its old implicit “empty filters means everything” behavior is deliberately disabled; an empty legacy selection upgrades to paused. Select **All items: ON** explicitly to restore that scope.

## Test Run preview

Click **Test Run** to pause this Rebalancer and scan the selected items. No items are inserted or extracted and no transfer energy is charged. The scan runs incrementally and results are paginated separately from the filter editor; **Back** returns to configuration without restarting transfers.

Each preview row shows an item, its full estimated amount, and its destination. Hover over the icon for the full destination description, including coordinates, dimension, and storage priority. Items split across destinations have separate rows.

- Storage buses: show the block the bus faces, including Functional Storage controllers. Drawer selection behind a controller is managed by Functional Storage and is not exposed by AE2.
- Drives/ME chests implementing AE2's cell host API: show the block, location, and cell slot.
- Other storage: fall back to the storage handler's description. No guessed block location is presented.

This is a **conservative capacity estimate, not an exact transaction or reservation**. The planner simulates normal insertion and extraction, follows strictly higher priorities and preferred-storage rules, and deducts already planned incoming amounts from each destination's simulated capacity. AE2's generic API cannot jointly simulate all item types without mutation. Independent drawers or slots can therefore be underestimated, and space freed by planned outgoing transfers is not counted. Another importer or player can change stock during or after a scan. The preview estimates amounts eligible to move, not completion time or available transfer energy.

Scans examine at most eight keys/exhausted sources per server tick, stop after 4,096 source keys or 256 item/destination rows, and explicitly report partial results when limited. Topology changes, selection changes, resuming the node elsewhere, unavailable storage, or a timeout stop/invalidate the scan. A pending recovery buffer or offline grid prevents a new scan. **Refresh** rescans current storage.

## Transfer behavior

- One channel and 2 AE/t idle usage.
- Only item transfers, between actual mounted inventories. Cells remain installed.
- Strictly increasing storage priority; equal-priority storage is left alone.
- Highest-priority destinations first, then AE2's preferred-storage rule within each priority group.
- Defaults: up to 4,096 items and 64 source checks per active tick. Throughput, power cost, idle delay, and mount limit are configurable in server settings.
- Destination simulation, source-specific extraction, rollback, and a saved recovery buffer for unexpected short insertion.
- The recovery buffer contributes drops if the block is removed.

“Settled” means no selected item can currently move to strictly higher-priority storage. Full drawers may therefore be settled while stock remains in cells. Continuous mode checks again later. Lack of transfer power does not falsely complete a useful migration.

The default limit is 512 mounted inventories, counting each cell separately. A fault stops transfers and logs the reason. Block art remains a placeholder using vanilla textures.

## Build and run

Install a Java 25 JDK and set `JAVA_HOME`, then build using the included Gradle wrapper:

```sh
./gradlew build
```

Output: `build/libs/me-rebalancer-0.3.2-dev.jar`.

`./gradlew build` resolves AE2 and GuideME from public Maven repositories; an FTB installation is not required. Optional `-PpackModsDir="/path/to/FTB Evolution 2/mods"` uses the exact pack jars and adds Functional Storage and Titanium to the development runtime. The build helper `tools/build.sh` can also detect Homebrew OpenJDK 25 and use project-local Gradle caches.

Isolated GUI development client (uses `run/`, separate from FTB):

```sh
./tools/build.sh '-PpackModsDir=/path/to/FTB Evolution 2/mods' runClient
```

Isolated headless integration test (uses `build/game-test-run/`):

```sh
./tools/build.sh '-PpackModsDir=/path/to/FTB Evolution 2/mods' runGameTestServer
```

## Validation

- **33 JUnit tests** cover transfers, explicit selection, and simulation-only preview planning, including shared capacity, split destinations, long counts, filters, preference, topology changes, and work limits.
- **A real Minecraft menu GameTest** verifies ghost stack conservation across click types, filter save/reload including components, legacy selection migration, explicit all-items mode, 64-bit counter synchronization, and distance validation. It also checks preview slot safety and offline Test Run behavior. A second GameTest verifies destination lookup against a real Functional Storage controller block and the actual AE2 bus wrapper. All three required tests pass (our two tests plus Minecraft's built-in example).
- Isolated GUI-version client startup and AE2 bridge self-check passed with the exact pack dependencies.
- **28 offline resource and binary contract checks** pass:

```sh
python3 tools/verify_pack.py '/path/to/FTB Evolution 2'
```

GUI layout, interactive clicks, and scaling still require manual in-game validation. Full transfers under a live pipeline, grid changes, block removal with buffered items, and large-network performance also remain to be validated.

See [API investigation](docs/AE2_API.md) for internal integration boundaries and remaining limitations.

Crafting recipe: four diamonds in the corners, Certus Quartz Crystal at top center, two Fluix ME Glass Cables at middle left/right, Logic Processor in the center, and Redstone Dust at bottom center.

## GitHub builds

Pushes to `main` and pull requests run the Java 25 build, unit tests, and headless Minecraft GameTests. Successful runs upload a development jar under the workflow's **Artifacts** section. Test reports are uploaded even if the build fails.

These artifacts are development builds, not published releases. Open an [issue](https://github.com/neldridge/me-rebalancer/issues) with the mod version, Minecraft/AE2 versions, reproduction steps, and a relevant log excerpt when reporting a bug.
