<!-- 478c3ea5-5e46-4ff4-8153-6b9c211ec5ed -->
---
todos:
  - id: "plan-records"
    content: "Record the merged Stage 3 plan and update the parent plan status"
    status: completed
  - id: "entities"
    content: "Add the storage allocation entity tiers and their JPA tables"
    status: completed
  - id: "factory"
    content: "Add the engine repository, allocation factory and Spring wiring"
    status: completed
  - id: "query"
    content: "Add the storage allocation query, handler and over-commit guard"
    status: completed
  - id: "admin"
    content: "Add the storage pool allocation collection and the allocation item endpoints"
    status: completed
  - id: "bootstrap"
    content: "Seed declared or fallback allocations for every storage pool"
    status: completed
  - id: "config"
    content: "Extend the storage pools configuration binding and examples with allocations"
    status: completed
  - id: "tests"
    content: "Add Java unit tests and the Python admin storage allocation tests"
    status: completed
  - id: "build"
    content: "Compile and run the Java unit tests"
    status: completed
  - id: "suites"
    content: "Run the Python docker and mock suites against a local broker"
    status: completed
  - id: "commit"
    content: "Commit through bin/agent-commit after approval, push the fork and open the pull request"
    status: pending
isProject: false
---
# Plan: Storage allocations and capacity over time

Date: 2026-11-28
Scope: `Calycopis-broker` — the storage allocation entity family, its
repository and factory, a pool-scoped capacity query, the admin allocation
endpoints, allocation seeding for the storage bootstrap, the matching
configuration binding, example configuration, and the Java and Python tests.
Booking, offers, reservations, policies and the published schema are untouched.
Issues: [uksrc/Calycopis-broker#78](https://github.com/uksrc/Calycopis-broker/issues/78)
— storage resource pools, stage 4; [#169](https://github.com/uksrc/Calycopis-broker/issues/169)
— booking across resource types; [#216](https://github.com/uksrc/Calycopis-broker/issues/216)
— the eventual generic resource pool abstraction.
Parent: [20261125-01-storage-resource-pools-and-booking-across-resource-types](20261125-01-storage-resource-pools-and-booking-across-resource-types-059e5e37.plan.md).
Builds on: [20261128-01-storage-pools-storage-classes-and-the-admin-api](20261128-01-storage-pools-storage-classes-and-the-admin-api-48dcd834.plan.md),
commit `a3181e2b`, merged as [PR #236](https://github.com/uksrc/Calycopis-broker/pull/236)
at `91fa9cc6`.
Branch: `20261128-zrq-storage-allocations-and-capacity`, created from `main` at
`91fa9cc6`.
Result: implemented on the branch, with Java and Python tests passing. No
commit, push or pull request yet.

## Target

A storage pool’s supply is the sum of its allocations over time. An
allocation belongs to one pool, starts at an instant, may end at a later
instant, and supplies a positive size in GiB. The pool has no separate global
capacity setting.

Administrators can list, create, read, update and delete allocations through
the storage pool collection and the storage allocation item paths. A change
that would leave a live storage reservation uncovered in any 60-second block
is refused with `409 Conflict`, and the allocation row is left unchanged.

Every seeded storage pool gets a declared allocation, or a fallback open-ended
`1024` GiB allocation when it declares none. Both `default-volume` and
`default-bind` therefore have supply on a fresh deployment.

No request or booking path reads storage allocations after this stage. Storage
offer generation, reservation creation, storage lifecycle behaviour and the
Python session suites must remain unchanged.

This is stage 4 because stage 5 attaches each reservation to its pool and
stage 6 moves storage offer generation inside the pool. Stage 4 supplies the
capacity those stages will read, but does not read it itself.

## Where we are

Stage 3 is merged on `main` in PR #236 at `91fa9cc6`; its implementation commit
is `a3181e2b` and its plan record is `1e79103d`. Local `main`, `origin/main`
and `uksrc/main` all point at `91fa9cc6`. The worktree is clean apart from two
unrelated untracked notes.

The storage pool tier already exists:

* `engine/entities/storage/pool/AbstractStorageResourcePool.java`,
  `AbstractStorageResourcePoolEntity.java`, `StorageClass.java` and the
  `simple/` tier.
* `spring/jpa/SpringStorageResourcePoolEntityRepository.java`,
  `spring/pools/SpringSimpleStorageResourcePoolEntityFactoryImpl.java` and the
  allocation-free `StoragePoolBootstrap`.
* `spring/webapp/AdminStoragePoolsController.java` with create, list and select
  under `/admin/pools/storage`.
* `StoragePoolSpec` carries `name`, `description`, `priority`, `enabled` and
  `storageClass`, but no allocations.
* `config/pools.yaml` declares the two default storage pools and states that
  allocations arrive in a later stage.

The compute allocation tier is the precedent:

* `engine/entities/compute/allocation/` contains the abstract allocation
  interface, entity, the `simple/` entity and factory.
* `engine/functional/booking/compute/simple/SimpleComputeAllocationQuery.java`
  holds the block-grid guard, and `SimpleComputeAllocationQueryHandler` plus
  `spring/booking/compute/simple/SpringSimpleComputeAllocationQueryHandlerImpl.java`
  wire it to `JdbcTemplate`.
* `spring/webapp/AdminComputePoolAllocationsController.java` and
  `AdminComputeAllocationsController.java` provide the collection and item
  endpoints.
* `spring/pools/ComputeAllocationSpec.java` and `ComputePoolBootstrap` seed
  declared allocations, or one fallback allocation for a pool with none.
* `ComputeAllocationValidation`, `ComputeAllocationRequest` and
  `ComputeAllocationResponse` keep the admin JSON off-schema.

Storage reservations already record a min/max requested and offered size and
hold the hull of the compute preparing, available and releasing windows. They
do not yet carry a pool or a class. That matters for the guard below.

The parent plan still records stage 3 as open with its merge pending, and the
stage 3 plan still says “open as PR #236”. Both are stale and are corrected as
the first change in this stage.

## Design

### Allocation entity tier

Mirror the compute allocation shape with storage names and one size dimension.

| File | Role |
|---|---|
| `engine/entities/storage/allocation/AbstractStorageAllocation.java` | Interface extending `Component`, with `getPool()`, `getStart()`, `getEnd()` and `getSize()`. |
| `engine/entities/storage/allocation/AbstractStorageAllocationEntity.java` | Abstract `@Entity`, `@Inheritance(JOINED)`, `@Table(name = "abstractstorageallocations")`; holds the pool, `startseconds`, nullable `endseconds` and `size`. |
| `engine/entities/storage/allocation/simple/SimpleStorageAllocation.java` | Interface extending `AbstractStorageAllocation`, with `KIND_DISCRIMINATOR = uri:storage-allocation`. |
| `engine/entities/storage/allocation/simple/SimpleStorageAllocationEntity.java` | Concrete `@Entity`, `@DiscriminatorValue("uri:storage-allocation")`, `@Table(name = "simplestorageallocations")`. |
| `engine/entities/storage/allocation/simple/SimpleStorageAllocationEntityRepository.java` | Engine repository interface extending `AbstractEntityRepository`, with `findByPool()` and `findAll()`. |
| `engine/entities/storage/allocation/simple/SimpleStorageAllocationEntityFactory.java` | Factory interface: `select`, `list`, `create`, `update`, `delete` and `wouldOverCommit`. |
| `engine/entities/storage/allocation/simple/SimpleStorageAllocationEntityFactoryImpl.java` | Framework-neutral factory using the repository and the allocation query handler. |
| `spring/jpa/SpringStorageAllocationEntityRepository.java` | Spring Data repository extending the engine interface. |
| `spring/pools/SpringSimpleStorageAllocationEntityFactoryImpl.java` | `@Component` subclass supplying the repository and handler. |

The abstract entity carries:

* `pool`: `@ManyToOne(optional = false, fetch = FetchType.LAZY)` joined on
  `pool` to the pool `uuid`, nullable `false`.
* `startseconds`: non-null `long`, exposed as `Instant`.
* `endseconds`: nullable `Long`, exposed as a nullable `Instant`.
* `size`: non-null `long`, in GiB.

The entity validates in `setValues()`: a start is required, `size` must be
greater than zero, and a non-null end must be after the start. The admin layer
repeats these checks to produce a `400`, but a value that bypasses the admin
validation still fails in the entity rather than being stored.

An update changes the size and window only. The pool is not changed: moving an
allocation between pools would need a guard against both pools and is deferred.

`getWebappPath()` returns `/allocations/storage/`, matching the item path. The
kind is off-schema, like `uri:storage-pool`, `uri:compute-pool` and
`uri:compute-allocation`.

### Capacity scope before stage 5

Storage reservations do not yet have a pool column. The query can therefore
scope the supply side to the pool named in the admin path, but it cannot scope
the demand side to the same pool.

For this stage the guard treats every live storage reservation as demand
against the pool being changed. That is deliberately conservative: a custom
pool with a `1` GiB allocation will refuse to delete that allocation while any
live storage reservation of `1` GiB exists, even though the reservation would
eventually belong to one of the `1024` GiB default pools. Stage 5 adds the pool
to each reservation, which is the first point at which a later stage can scope
demand; stage 6 scopes the booking query. The plan does not invent a class
filter or a reservation-pool association before the column exists.

The alternative, summing allocations across all pools and comparing against
all reservations, would make the integration test for a `409` impossible
without changing the default allocations, and it would not answer the question
the admin endpoint asks: “can this pool’s supply cover the live demand?”. The
conservative per-pool supply rule is the one to implement.

This limitation is stated in the query comment and in the plan’s status notes
so stage 5 knows exactly what to narrow.

### Capacity query

Add `engine/functional/booking/storage/simple/SimpleStorageAllocationQuery.java`
with `BLOCK_STEP_SECONDS = 60L`, a `DATABASE_QUERY` and a `build(...)` method.

The SQL follows the compute guard:

1. `ReservedBlocks` selects one row per live storage reservation, using the
   same live session phases as the compute guard:
   `OFFERED`, `ACCEPTED`, `WAITING`, `PREPARING`, `AVAILABLE`, `RUNNING`,
   `RELEASING`.
2. The reservation start block is the floor of the earliest of the preparing,
   available and releasing start instants divided by `60`.
3. The reservation end block is the ceiling of the greatest of the three
   window ends divided by `60`; the `generate_series` uses `BlockEnd - 1`, so
   an end exactly on a block boundary does not extend into the next block.
4. The used size is `COALESCE(SimpleStorageReservations.maxofferedsize,
   SimpleStorageReservations.maxrequestedsize)`. The offered size is what the
   reservation actually holds; the requested size is the fallback for a row
   that predates the offered columns.
5. `ReservedBlocksByBlock` expands each reservation over its blocks with
   `generate_series` and sums the reservations sharing a block.
6. `OverCommitted` keeps blocks where the used size is greater than the
   pool’s other allocations plus the candidate allocation. The candidate is
   supplied as literals, including `NULL` for an open end and zero for a
   removal.
7. The final select returns `COUNT(*) AS OverCommittedBlocks`.

The allocation window arithmetic is identical to compute:

* an allocation covers a block when
  `(Allocations.startseconds / 60) <= BlockStart`;
* an allocation with no end covers every block from its start;
* an allocation with an end covers blocks where
  `((Allocations.endseconds + 59) / 60) > BlockStart`.

The query must not mention `storageclass`. Both `VOLUME` and `BIND` pools use
the same query and the same arithmetic; the class is accounting metadata in
this stage, not a filter.

Add `SimpleStorageAllocationQueryHandler extends AbstractQueryHandler<Long>`
and `spring/booking/storage/simple/SpringSimpleStorageAllocationQueryHandlerImpl.java`,
mirroring the compute handler with a `JdbcTemplate` and a `Long` row mapper.

`SimpleStorageAllocationEntityFactoryImpl.wouldOverCommit(...)` builds the
query from the pool UUID, excluded allocation UUID, candidate size, start and
end, runs it through the handler, and returns true when the single count is
greater than zero.

### Admin API

Add two controllers under the existing `/admin/**` security matcher, which
already requires the `ADMIN` authority. No `SecurityConfig` change is needed.

`spring/webapp/AdminStoragePoolAllocationsController.java` under
`/admin/pools/storage/{poolUuid}/allocations`:

| Verb | Path | Request | Response |
|---|---|---|---|
| `GET` | `/admin/pools/storage/{poolUuid}/allocations` | — | `200 OK`, array of allocation bodies, or `404` for an unknown pool |
| `POST` | `/admin/pools/storage/{poolUuid}/allocations` | `{"size": ..., "start": ..., "end": ...}` | `303 See Other`, `Location: /admin/allocations/storage/{uuid}` |

`spring/webapp/AdminStorageAllocationsController.java` under
`/admin/allocations/storage`:

| Verb | Path | Request | Response |
|---|---|---|---|
| `GET` | `/admin/allocations/storage/{uuid}` | — | `200 OK`, allocation body, or `404` |
| `POST` | `/admin/allocations/storage/{uuid}` | `{"size": ..., "start": ..., "end": ...}` | `200 OK`, updated body, or `409` when it would over-commit |
| `DELETE` | `/admin/allocations/storage/{uuid}` | — | `204 No Content`, or `409` when removal would over-commit |

`StorageAllocationRequest` is a hand-written record with `Long size`,
`Instant start` and `Instant end`. `StorageAllocationResponse` is a record with
`UUID uuid`, `UUID pool`, `long size`, `Instant start`, `Instant end` and
`Instant created`. The pool is repeated in the item response because the item
path carries only the allocation identifier.

`StorageAllocationValidation` mirrors `ComputeAllocationValidation`:

* `size` is required and must be greater than zero.
* a missing `start` means `Instant.now()`.
* a missing `end` is open ended.
* a present `end` must be after the resolved start.

All writes run inside
`BookingTransactionHandler.book(SimpleStorageReservation.KIND_DISCRIMINATOR.toString(), ...)`,
so they take the storage booking lock and cannot commit against a stale
reservation view. The create path also runs in that transaction and uses a
`NO_ALLOCATION` UUID for the excluded allocation. Adding supply cannot
over-commit, but the lock keeps create, update and delete serialised and keeps
the implementation symmetrical with compute.

The controller resolves the pool from the collection path, rejects a
non-UUID pool or allocation identifier with `400`, and rejects an unknown
pool or allocation with `404`. The guard runs before `update()` or `delete()`,
so a `409` leaves the row unchanged.

Add three `ProblemType` values:

* `INVALID_STORAGE_ALLOCATION` (`400`, `invalid-storage-allocation`)
* `STORAGE_ALLOCATION_NOT_FOUND` (`404`, `storage-allocation-not-found`)
* `STORAGE_ALLOCATION_OVERCOMMITTED` (`409`, `storage-allocation-overcommitted`)

### Bootstrap and configuration

Add `spring/pools/StorageAllocationSpec.java` with `Long size`, `Instant start`
and `Instant end`, and add `List<StorageAllocationSpec> allocations` to
`StoragePoolSpec`.

Extend `StoragePoolBootstrap`:

* inject `SimpleStorageAllocationEntityFactory`;
* add `DEFAULT_ALLOCATION_SIZE_GIB = 1024L` and `SEEDED_ALLOCATION_START =
  Instant.EPOCH`;
* after a new pool is created, call `seedAllocations(pool, spec)`;
* if the pool declares no allocations, or the list is absent/empty, create one
  open-ended `1024` GiB allocation starting at the epoch;
* for each declared allocation, skip a null entry, a missing or non-positive
  size, and an end that is not after the resolved start, with a warning;
* a declared allocation with no start starts at the epoch, and no end is open
  ended.

The seed stays idempotent by name: a second run finds each pool and creates no
second pool or allocation. The fallback is applied to both defaults, including
`default-bind`.

Update the storage section of `config/pools.yaml` to document allocations:
`size` in GiB, an optional start (epoch when absent), an optional end (open
ended when absent), and the `1024` GiB fallback for a pool with none. Keep the
two defaults without declared allocations so the fallback is exercised.

`PoolsSettingsImpl` already binds `calycopis.broker.pools`; the existing
`StoragePoolSpec` binding picks up the new list without a property-source
change.

### Tests

Java unit tests:

* `SimpleStorageAllocationQueryTest` checks that `build()` substitutes every
  placeholder, reads supply from `AbstractStorageAllocations`, sums
  `Allocations.size` and `UsedStorage`, scopes allocations to the pool UUID,
  treats a missing end as open ended, rounds an end up to its block, expands
  blocks with `generate_series`, and contains no `storageclass` predicate.
* `SimpleStorageAllocationEntityTest` checks the entity accepts a valid window
  and rejects a zero or negative size, a missing start, and an end at or
  before the start.
* Extend `StoragePoolBootstrapTest` with a stub allocation factory and check:
  both defaults get the `1024` GiB fallback; a declared allocation wins; a
  second run creates no second allocation; invalid entries are skipped; and a
  configured pool gets its allocation alongside the defaults.
* Extend `PoolsSettingsImplTest` to bind a storage pool with a declared
  allocation and check its size and window reach the settings bean.

Python integration tests, in `tests/python/any/test_admin_storage_allocations.py`:

* create on a pool answers `303` with a `Location` under
  `/admin/allocations/storage/`, and the item is readable with the right pool,
  size and window;
* the default volume and bind pools each list exactly one seeded open-ended
  `1024` GiB allocation;
* update resizes and moves the window; delete answers `204` and the item is
  then `404`;
* unknown pool and allocation are `404`; malformed UUIDs and invalid sizes or
  windows are `400`;
* every verb is `401` without credentials and `403` for a local identity;
* a pool with a `1` GiB allocation and a live storage reservation refuses a
  delete with `409`, the allocation remains readable and unchanged, and the
  delete succeeds after the reservation is released;
* a pool with two current `1` GiB allocations and a live `2` GiB demand
  refuses the delete of either allocation, so the guard sees the combined
  supply;
* a pool with a current `2` GiB allocation and a future or already-ended `2`
  GiB allocation refuses the delete of the current allocation while a live
  `2` GiB demand exists, showing the out-of-window allocation contributes
  nothing;
* the same create/update/delete flow works on a `VOLUME` pool and a `BIND`
  pool, showing the class does not alter the allocation query.

The live reservations are made through the existing request client. A data
resource creates a storage reservation with the default `1` GiB size, so two
data resources give the `2` GiB demand used by the window test. The guard’s
conservative demand rule is what makes those reservations visible to a custom
pool in this stage.

Existing Python allocation, storage pool and session suites must not change
their expectations. No OpenAPI schema, offer, policy or request-path test is
added or altered.

## Acceptance

* Storage allocation create, list, select, update and delete work through the
  admin API; create redirects with `303`.
* Malformed sizes and windows are `400`; unknown pools and allocations are
  `404`; unauthenticated requests are `401`; local identities are `403`.
* Over-committing updates and deletes are `409`, and the refused row is
  unchanged.
* The query sums overlapping allocations per block, an allocation contributes
  nothing before its start, an ended allocation stops after its end, and an
  open-ended allocation covers every block from its start.
* Both `default-volume` and `default-bind` have an open-ended `1024` GiB
  fallback allocation on a fresh deployment.
* Declared allocations are seeded instead of the fallback, and an invalid
  entry is skipped rather than failing startup.
* `VOLUME` and `BIND` pools use the same query; no class predicate appears.
* No booking or request path reads storage allocations; the Python session
  suites pass unchanged.
* The Java unit tests pass, the Python `docker any` and `mock any` suites pass,
  and the new admin storage allocation tests pass.
* The parent plan and the stage 3 plan record the actual stage 3 merge at
  `91fa9cc6`.

## Out of scope

* A `pool` or `storageClass` column on storage reservations; stage 5.
* Scoping the guard’s demand side to one pool; stage 5/6.
* A storage reservation booking query, storage offers, or `makeOffers`.
* Storage pool policies and multiple storage pools in the request path.
* Any published schema, OpenAPI model, security matcher, offer generation or
  session lifecycle change.
* A generic resource pool or generic allocation abstraction.

## Risks

* The SQL is the substantial part. Copy the compute block arithmetic exactly,
  then change the reservation table and the size column. Keep the storage
  query separate until the generic stage.
* The demand side is intentionally conservative until stage 5. A `409` can be
  produced by a reservation that will later belong to another pool. This is a
  known limitation, not a bug to hide; document it in the query and the status.
* An allocation create is an increase in supply and cannot over-commit. The
  lock is still taken so all allocation writes are serialised; a test should
  not assert a `409` for create.
* The bootstrap’s fallback must apply to both defaults, including the bind
  pool, even though bind capacity is accounting-only in later stages.
* A missing `start` in an admin request means `Instant.now()`, while a missing
  `start` in configuration means the epoch. Keep the two paths distinct and
  test them separately.
* Existing `StoragePoolBootstrapTest` constructs the bootstrap directly, so its
  constructor change needs the stub allocation factory in every test setup.

## Verification

Run the Java build and unit tests from the repository root, with Maven in
`java/`:

```bash
source bin/versions.sh config.yaml
(
    cd java
    MAVEN_USER_HOME="${CALYCOPIS_SCRATCH:?}/m2" \
        mvn -Dmaven.repo.local="${CALYCOPIS_SCRATCH:?}/m2/repository" test
)
```

Build the broker image from the packaged jar only if the Python suites need a
new image. Run the Python suites against a docker-profile broker and then a
mock-profile broker, following `tests/python/bin/run-tests.sh`. Report the
counts for:

* the new `any/test_admin_storage_allocations.py` file;
* `docker any`;
* `mock any`.

Do not report a suite as passing without the exact command and broker profile.

## Commit

Proposed commit message, subject to explicit approval of the exact change set:

```text
Add storage allocations and capacity over time

- add the storage allocation entity tier, factory and repository
- add the pool-scoped storage allocation capacity guard
- add the storage allocation admin endpoints
- seed declared or fallback allocations for every storage pool
- bind storage allocations in the pools configuration
- correct the stage 3 plan records and update the parent plan
- add Java unit tests and Python admin storage allocation tests

AIMetrics: [
    {
    "interval": "2026-11-28T04:50:00/2026-11-28T05:40:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 100,
      "units": "%"
      }
    }
  ]
```

Commit with `bin/agent-commit` only after that approval. Push the branch to the
fork and open a pull request against `uksrc/Calycopis-broker` using the upstream
token, as described in `AGENTS.md`.

## Status

status: implemented
details: Implemented on branch `20261128-zrq-storage-allocations-and-capacity`
  from `main` at `91fa9cc6`. Java compile and all 140 unit tests pass. The
  Python `docker any` suites passed 207 tests, the `mock any` suites passed 201,
  and the new admin storage allocation tests passed 21. No commit, push or pull
  request yet. Until storage reservations carry a pool, the guard’s demand side
  is every live storage reservation; stage 5 adds the column that lets a later
  stage narrow it.

## AIMetrics

```text
AIMetrics: [
    {
    "timestamp": "2026-11-28T04:46:42Z",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 100,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-28T04:50:00/2026-11-28T05:40:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 100,
      "units": "%"
      }
    }
  ]
```
