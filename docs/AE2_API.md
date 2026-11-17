# AE2 API investigation

## Finding

A priority-aware rebalancer is feasible, but the public combined-storage API alone cannot provide the control we need. This source prototype uses a version-pinned internal bridge. Compilation, all 33 unit tests (15 transfer, 7 selection, 11 preview), and both real Minecraft integration tests pass. The isolated client starts with the exact pack dependencies, and the version-pinned Mixin bridge passes its startup self-check. Real-world transfer reliability still requires gameplay validation.

The exact installed AE2 jar is `appliedenergistics2-26.1.13-beta.jar`, SHA-256:

`6474119c645a21f509da518845ab42bff84d496f80a86ce5e551d7a4745b4893`

The class-file inspection utility is kept in `tools/inspect_class.py` because no JDK tools were available in the environment. It confirms the descriptors in the installed binary, rather than assuming current online documentation matches it.

## Public contracts

- `IStorageService.getInventory()` returns the combined `MEStorage`.
- `MEStorage` supports simulation/modulation for insertion and extraction, preferred-storage checks, and available-stack enumeration.
- `IStorageProvider.mountInventories(IStorageMounts)` supplies inventories and priorities **to AE2**. It is not an authoritative query for existing mounts; calling it independently can have provider-specific side effects or create fresh wrappers.
- `IStorageService` does not publicly expose the live mount table or a priority-restricted transfer method.
- `IManagedGridNode`, `IGridTickable`, and `IActionSource.ofMachine` provide the node lifecycle, scheduling, and action context.

References:

- [Storage service interface](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/main/src/main/java/appeng/api/networking/storage/IStorageService.java)
- [Network storage implementation](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/main/src/main/java/appeng/me/storage/NetworkStorage.java)
- [Storage service implementation](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/main/src/main/java/appeng/me/service/StorageService.java)
- [Drive implementation](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/main/src/main/java/appeng/blockentity/storage/DriveBlockEntity.java)
- [NeoForge 26.1.2 development template](https://github.com/NeoForgeMDKs/MDK-26.1.2-ModDevGradle)

Online links track upstream main; the offline checks verify the specific members used against the installed version.

## Internal bridge

`NetworkStorageAccessor` exposes only:

- `priorityInventory`: the actual mounted inventories grouped by priority, read without editing the table.
- `mountsInUse`: AE2's recursion/mount-change guard.
- `isQueuedForRemoval`: avoids touching a mount removed during an operation.
- `flushQueuedOperations`: finishes deferred mount changes after releasing the guard.

The adapter snapshots mount references inside the guard, invokes their normal `MEStorage` methods with a machine action source, then releases the guard and flushes changes in a `finally` block. This prevents an attached interface from recursively entering the same combined network inventory. The storage cache is invalidated after mutation or failure.

`DelegatingStorageAccessor` adds an invoker for `DelegatingMEInventory.getDelegate()`. Destination labels match mounted drive cells to the public `IChestOrDrive` API, or match a bus wrapper's current delegate to `StorageBusPart.getInternalHandler()`. The bus owner supplies its facing block and loaded position; neither inventories nor providers are remounted for this lookup. The native storage-bus subclass and Functional Storage controller label are covered by a GameTest. Unknown providers retain their own storage description.

Both mixin configuration and metadata reject unsupported integration versions instead of silently falling back to blind extraction and reinsertion. An AE2 upgrade requires descriptor review and runtime tests.

## Transfer and conservation rules

Ordinary migration requires destination priority greater than source priority. Simulation checks destination capacity and source availability before charging transfer power and extracting. Destination order follows descending priority and preferred storage within each priority. Direct source extraction prevents the combined network from accidentally selecting a different inventory.

An extracted batch is tracked as a pending transfer before destination modulation. Any remainder is returned to its source. If that return is refused or the source disappears, the remainder remains in the block's saved buffer. While the buffer is nonempty, no new source stock is extracted. Recovery may insert into any remaining mounted storage, including lower-priority storage, to preserve items. The buffer is serialized using `AEItemKey.CODEC` plus a separate long amount, preserving item components without an oversized ItemStack. Native block-entity drop handling includes buffered items.

A useful transfer that cannot receive power keeps run-once mode pending. An entire pass with no useful destination is settled. The scan cursor continues across ticks; topology/priority changes reset it.

## Limits requiring validation

- This is ordinary server-thread inventory movement, not a database transaction. It relies on storage implementations honoring simulation and returned transfer counts. A handler that mutates and then throws cannot be made atomic by this addon. Faults stop automatic retries and are logged for inspection.
- Void upgrades and other intentionally destructive inventories may report successful acceptance for discarded items. The prototype follows the destination's normal insertion contract. It does not detect or bypass voiding.
- Aliases of the same physical inventory behind different storage-bus wrappers cannot always be recognized. Duplicate identical mounted objects are rejected. Exposing one inventory redundantly at conflicting priorities needs runtime testing and may remain unsupported.
- Cross-grid interfaces, virtual/infinite cells, creative storage, and third-party transfer wrappers require testing. An infinite source cannot finish a migration into an infinite destination.
- A source inventory's available-type enumeration is a whole-snapshot operation in AE2. Item and candidate budgets bound transfer work, but do not impose a hard time bound on a third-party handler's enumeration. Large-network profiling remains necessary.
- Chunk/world crash behavior has the same multi-inventory save ordering limits as normal Minecraft automation. The pending buffer protects ordinary short insertion and save/reload recovery; it is not a crash-atomic item ledger.
- Version 0.2.0-dev includes a server-backed ghost-filter menu and client screen. Real Minecraft backend tests pass; visual layout and interactive client behavior still need in-game verification.

## Read-only previews

`PreviewPlanner` only calls `extract` and `insert` with simulation enabled and never receives a power service. It scans incrementally, rejects changing/duplicate mounts, aggregates item/destination rows, and reserves destination space conservatively across all planned incoming types. Independent slots/drawers may be underestimated because the generic MEStorage API cannot simulate an entire multi-type transaction. Planned outgoing stock does not create simulated free capacity. The report is an estimate that ignores transfer duration and available power.

Preview state is scoped to the open menu and not persisted. Rows sync through read-only item slots with amount and destination components, using normal menu synchronization. Counter/scan state uses validated server-owned data slots; rows cannot be cloned or moved into real inventory. Selecting Test Run pauses the node. Changes to filters, topology, or running state invalidate the scan, and explicit limits prevent unbounded result lists.
