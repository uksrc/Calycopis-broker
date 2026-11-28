<!-- 0c2eeee5-ed28-423e-982d-d83058687fdc -->
---
todos:
  - id: "branch"
    content: "Create the branch from main at 3224d127"
    status: completed
  - id: "columns"
    content: "Add requested and offered min/max size columns and accessors to SimpleStorageReservationEntity"
    status: completed
  - id: "defaults"
    content: "Default missing storage sizes to 1 GiB and normalise the minimum and maximum"
    status: completed
  - id: "window"
    content: "Derive the storage preparing, available and releasing windows from the compute reservation and the data prepare durations"
    status: completed
  - id: "bean"
    content: "Populate the size field in the storage response bean"
    status: completed
  - id: "tests"
    content: "Add the state size/window test and the mock, docker and response assertions"
    status: completed
  - id: "build"
    content: "Compile and run the Java unit tests"
    status: completed
  - id: "suites"
    content: "Run the Python suites against a local broker"
    status: completed
  - id: "commit"
    content: "Commit through bin/agent-commit after approval, push the fork and open the pull request"
    status: completed
isProject: false
---
# Plan: Storage reservations record size and hold the compute window

Date: 2026-11-27
Scope: `Calycopis-broker` — `SimpleStorageReservationEntity` and its interface,
the abstract storage reservation schedule constructor, the storage response
bean, the Java and Python tests that assert sizes and windows. Storage pools,
storage classes, capacity queries and the staged booking pipeline are not in
this stage.
Issues: [uksrc/Calycopis-broker#78](https://github.com/uksrc/Calycopis-broker/issues/78)
— storage resource pools, stage 2; [#169](https://github.com/uksrc/Calycopis-broker/issues/169)
— booking across resource types.
Parent: [20261125-01-storage-resource-pools-and-booking-across-resource-types](20261125-01-storage-resource-pools-and-booking-across-resource-types-059e5e37.plan.md).
Builds on: [20261127-01-rename-the-storage-resources-to-storage-reservations](20261127-01-rename-the-storage-resources-to-storage-reservations-ed813718.plan.md).
Branch: `20261127-zrq-storage-size-and-window`, to be created from `main` at
`3224d127`.
Result: implemented on the branch, commit pending.

## Target

A storage reservation records the size it requested and the size it offers, and
its preparing, available and releasing windows are absolute instants derived
from the session's compute reservation. The storage hull covers the compute
hull: storage prepares before compute, storage is available before data
preparation, its availability covers data download and compute execution, and
it releases after compute release.

This is stage 2 because every later storage stage needs a persisted size and a
real window. Stage 4 checks capacity over the reservation hull, stage 5 attaches
the pool and class to the reservation, and stage 6 matches storage offers using
the required interval. None of those can work while storage size is discarded at
construction and the storage schedule is anchored at the epoch.

The public request and response shapes stay the same. The schema already carries
`SimpleStorageSize` and `SimpleStorageResource.size`; this stage fills the field
that the schema already publishes.

## Where we are

Stage 1 is merged on `main` at `3224d127`. The internal entity tier is now
`AbstractStorageReservation`, `SimpleStorageReservation`, the mock simple
storage reservation and the Docker bind/volume storage reservations. The five
tables are `abstractstoragereservations`, `simplestoragereservations`,
`mocksimplestoragereservations`, `dockerbindmountstoragereservations` and
`dockervolumemountstoragereservations`.

The storage size is validated but not stored:

* `SimpleStorageReservationEntity` still carries `// TODO Add the storage fields ...`
  in its constructor.
* `MockSimpleStorageReservationValidatorImpl.validateSize` reads
  `IvoaSimpleStorageResource.size`, defaults the minimum and maximum to `1L` GiB
  and writes a normalised `IvoaSimpleStorageSize` back to the validated bean.
* The Docker bind and volume linkers build storage directly from an
  `IvoaAbstractStorageResource` template and bypass the storage validator, so
  those reservations have no size at all.
* The storage response bean inherits `IvoaSimpleStorageResource.size` from the
  schema, but `SimpleStorageReservationEntity.fillBean` never sets it.

The storage windows are not usable for booking:

* `AbstractStorageReservationEntity` takes its prepare start from
  `session.getPrepareStartInstantSeconds()` and its release start from its own
  prepare/available fields. The session lifecycle timing is never initialised on
  the offer path (`init` is commented out), so those fields remain zero.
* `AbstractDataResourceEntity` derives its prepare start from the storage's
  available start, so the data windows inherit the epoch-anchored storage window.
* The compute reservation, by contrast, stores absolute windows built from the
  compute offer: prepare start, available start and duration, and release start
  and duration.

The parser builds reservations in this order: executable, compute, storage, data,
volume mounts. The storage result therefore cannot see a data entity yet, but it
does see the validated data results through
`AbstractStorageReservationValidator.Result.getDataResourceResults()`, including
each data resource's prepare duration. The compute reservation is already on the
session when storage is built.

There is no storage offer yet. The validated storage result is the only offer on
the direct path. `SimpleStorageOffer`, the storage criteria, the storage booking
query and the pool offer factory arrive in stages 6 and 8.

## Design

### Size

Add four columns to `SimpleStorageReservationEntity`, inherited by the mock,
bind and volume entities:

| Column | Meaning |
|---|---|
| `minrequestedsize` | The minimum size requested by the validated storage result, in GiB |
| `maxrequestedsize` | The maximum size requested by the validated storage result, in GiB |
| `minofferedsize` | The minimum size offered for this reservation, in GiB |
| `maxofferedsize` | The maximum size offered for this reservation, in GiB |

Add the matching accessors to `SimpleStorageReservation`, following the compute
reservation's requested/offered pattern: `getMinRequestedSize()`,
`getMaxRequestedSize()`, `getMinOfferedSize()` and `getMaxOfferedSize()`.

The constructor reads `result.getObject()`:

* When it is an `IvoaSimpleStorageResource` with a non-null `size`, the validated
  minimum and maximum are the requested size.
* When the size is null, the object is an `IvoaAbstractStorageResource` (the
  Docker bind/volume template), or the request omitted a bound, default to
  `1L` GiB.
* Normalise so that the maximum is never less than the minimum. This matters
  because the mock validator starts `maxsize` at `1L` and leaves it there when a
  request supplies only a larger minimum.

Stage 2 has no pool offer to read. The validated size is the offer on the direct
path, so `minofferedsize` and `maxofferedsize` initially mirror the requested
values. The columns and accessors are shaped for the later booking path: stage 6
defines `SimpleStorageOffer`, and stage 8 builds pipeline reservations from it.
When an offer is available, the offered columns come from the offer and the
requested columns stay as validated. That later change does not need a schema
change.

`SimpleStorageReservationEntity.fillBean` sets the response's
`IvoaSimpleStorageSize` from the offered minimum and maximum. The response
schedule is already filled by `AbstractStorageReservationEntity.fillBean`; it
will carry the new absolute windows without a bean change.

No migration is needed. Hibernate runs with `ddl-auto: create`, so the columns
appear in `simplestoragereservations` on the next broker start.

### Window

Let:

| Symbol | Value |
|---|---|
| `Cps` | Compute reservation `prepareStartInstantSeconds` |
| `Cpd` | Compute reservation `prepareDurationSeconds` |
| `Cas` | Compute reservation `availableStartInstantSeconds` |
| `Cad` | Compute reservation `availableDurationSeconds` |
| `Crs` | Compute reservation `releaseStartInstantSeconds` |
| `Crd` | Compute reservation `releaseDurationSeconds` |
| `D` | The largest `getPrepareDuration()` in the storage result's data results, or `0` when there are none |
| `Spd` | The storage result's `getPrepareDuration()` |
| `Srd` | The storage result's `getReleaseDuration()` |

Set the storage fields as follows:

| Storage field | Value |
|---|---|
| `prepareDurationSeconds` | `Spd` |
| `prepareStartInstantSeconds` | `Cps - D - Spd` |
| `availableStartInstantSeconds` | `Cps - D` |
| `availableStartDurationSeconds` | `0L` |
| `availableDurationSeconds` | `(Cas + Cad) - (Cps - D)` |
| `releaseStartInstantSeconds` | `Crs + Crd` |
| `releaseDurationSeconds` | `Srd` |

The data entity takes its prepare start from the storage's available start. With
the formula above, the latest data resource finishes preparing exactly at `Cps`,
which is when compute preparation starts. The storage availability ends at the
compute availability end, so it covers the data download and the compute
execution. The storage release starts after the compute release ends, so the
storage hull covers the compute hull from `Cps` through `Crs + Crd`.

The storage constructor reads `session.getComputeReservation()` as the source of
truth. The parser builds compute before storage, so a missing compute reservation
is a programming error; fail fast with an `IllegalStateException` rather than
falling back to the session timing fields. Do not change the storage result's
`build(session)` signature in this stage: the compute reservation already carries
the offer-derived absolute schedule, and stage 6 will add the storage offer
without needing the compute offer here.

The lifecycle rules in `AbstractStorageReservationEntity.checkReleaseActionRules`
stay unchanged. They already make storage wait for compute and data to be
released. Placing the scheduled release start after the compute release end makes
the stored schedule agree with those rules.

### Tests

Add a state test, `tests/python/states/test_storage_reservation_size_window.py`,
and read-only helpers in `tests/python/states/conftest.py`:

* `storage_reservation(session_uuid)` returns the first reservation for a
  session with the four size columns from `simplestoragereservations` and the
  seven lifecycle columns from `lifecyclecomponents`.
* `compute_reservation_schedule(session_uuid)` returns the compute
  reservation's six lifecycle columns.
* `data_prepare_durations(session_uuid)` returns the stored prepare duration of
  each data resource, so the test can compute `D`.

The state test submits a request with an explicit compute reservation and an
HTTP data resource, so the broker auto-creates the storage reservation, reads
the OFFERED session from the database, and asserts:

* the stored requested and offered sizes are the default `1/1` GiB;
* the offer response carries the same size;
* the stored schedule starts after the epoch, not at zero;
* `storage.prepare_start <= compute.prepare_start`;
* `storage.available_start + D <= compute.prepare_start`;
* `storage.available_start + storage.available_duration >= compute.available_start + compute.available_duration`;
* `storage.release_start + storage.release_duration >= compute.release_start + compute.release_duration`.

The explicit-size path is asserted in the mock storage registration test, where
the mock platform supports a `SimpleStorageResource`. That test checks the
response `storage.size` for an explicit `SimpleStorageSize` and for an explicit
storage with no size. The auto-created data storage tests in the `any` suite add
the same `1/1` GiB response assertion. The docker suites assert the bind and
volume defaults: `file://` storage is bind-mounted and `http(s)` storage is
volume-mounted, and both carry `1/1` GiB when the request supplies no size.

The Java tests need no new fixture. The entity constructor is exercised through
the broker tests and the Python suites; the only Java test change is any import
or expected response changed by the new size field.

## Method

1. Create the branch from `main` at `3224d127`.
2. Add the four size columns and accessors to `SimpleStorageReservationEntity`
   and `SimpleStorageReservation`.
3. Add the default and normalisation helper to the simple entity constructor.
   Use one constant, `DEFAULT_SIZE_GIB = 1L`.
4. Replace the storage window constructor body with the compute-reservation
   formula above. Read the compute reservation from the session and the data
   prepare durations from the validator result. Keep the existing session and
   data links untouched.
5. Set the response size in `SimpleStorageReservationEntity.fillBean`.
6. Add the state test and database helpers, and extend the mock and docker
   assertions.
7. Compile, run the Java tests and the Python suites, then inspect the stored
   sizes and windows.

Keep this as one commit. The size columns and the window derivation are one
behaviour change: a reservation without a size or a usable window is not a
storage booking.

## Verification

```bash
# In the agent sandbox the Maven cache and the version properties need a
# workspace-local path. Outside the sandbox the plain commands are enough.
source bin/versions.sh config.yaml
export MAVEN_USER_HOME=/Calycopis/agents/scratch/m2
export MAVEN_OPTS="-Dmaven.repo.local=/Calycopis/agents/scratch/m2/repository"

pushd java
    ./mvnw -q clean compile
    ./mvnw -q test
popd
```

```bash
pushd tests/python
    pytest -q any
    pytest -q mock
    pytest -q states
    pytest -q docker
popd
```

The Python suites need a broker and a database. `ddl-auto: create` rebuilds the
schema at startup, so the four new columns appear on the next start. `states`
needs the database settings from `/etc/calycopis/database.yaml`; see
`docs/deployment.md` for the broker configuration the state suites expect.

Check the stored columns directly:

```sql
SELECT
    s.uuid,
    z.minrequestedsize,
    z.maxrequestedsize,
    z.minofferedsize,
    z.maxofferedsize,
    s.prepare_start_instant_seconds,
    s.available_start_instant_seconds,
    s.available_duration_seconds,
    s.release_start_instant_seconds,
    s.release_duration_seconds
FROM
    abstractstoragereservations s
JOIN
    simplestoragereservations z
ON
    z.uuid = s.uuid
WHERE
    s.session = :session_uuid;
```

## Acceptance

* An explicit storage request persists its requested and offered minimum and
  maximum sizes, and the offer response carries the schema's `size`.
* An auto-created storage result and a Docker bind or volume storage result with
  no requested size persist `1/1` GiB.
* Stored storage starts are absolute instants, not zero, and the storage hull
  covers the compute hull: storage prepares no later than compute, is available
  before data preparation, remains available through compute execution, and
  releases no earlier than compute release.
* The storage and data lifecycle still reaches `AVAILABLE` and releases in order;
  the `mock` and `docker` suites pass.
* No storage pool, storage class, allocation, policy, booking query or capacity
  check is added. `git diff` over the OpenAPI sources, the HTTP paths and
  `src/main/resources` is empty.
* `./mvnw clean compile` is green and `./mvnw test` passes.
* One commit. The diff is limited to the storage entity and interface, the
  response bean, the tests and the plan record.

## Risks

* **The compute reservation may not exist.** The parser builds compute first,
  but a test or a future path that builds storage alone would now fail fast.
  That is deliberate: a storage reservation without a compute candidate has no
  window to hold, and silently using the epoch would recreate the bug this stage
  fixes.
* **The data prepare duration.** Storage must be available before every data
  resource starts preparing. Using the maximum, not the first or the average,
  is what keeps the last data resource ready at compute preparation. A storage
  result with no data results uses `0`.
* **The release tail.** Storage release starts after the compute release ends,
  not at the compute release start. That is what makes the hull cover the
  compute hull, while the existing lifecycle rules still wait for compute and
  data to reach `RELEASED`.
* **The availability window.** Storage availability ends at the compute
  availability end. The release window starts later, after compute release.
  This leaves a gap between availability and release rather than stretching
  availability through the release tail; the booking query measures the hull,
  not the availability window alone.
* **The requested/offered split.** Stage 2 mirrors the offered values from the
  validated size because no pool offer exists. Do not build a capacity query
  against an offered value that a pool has not produced; stage 6 replaces the
  source of the offered columns.
* **The joined table.** The size columns live on `simplestoragereservations`.
  A query that reads only `abstractstoragereservations` will not see them.
  Join through the simple table, as the state helper does.
* **The data windows move.** Data resources derive their schedule from storage,
  so fixing storage shifts the data windows too. The lifecycle suites and the
  state ordering assertions are the check that this is an improvement rather
  than a regression.

## Out of scope

Storage pools, storage classes, allocations, policies, storage offers, booking
queries and capacity enforcement. The admin API. The schema `SimpleStorageSize`
definition, the bean types and the HTTP paths. The session's own lifecycle
initialisation, except that storage no longer depends on it. Documentation and
configuration examples.

## Status

status: implemented
details: Implemented on branch `20261127-zrq-storage-size-and-window` from
  `3224d127`, commit `9147d162`, and merged as
  [PR #235](https://github.com/uksrc/Calycopis-broker/pull/235) at `aacee00d`.
  Java compile and all 105 unit tests pass. The Python `any` suite passed 106
  tests, the `mock` suite passed 57, the `docker` suite passed 63, and the new
  state size/window test passed.

## AIMetrics

```text
AIMetrics: [
    {
    "interval": "2026-11-27T18:28:49/2026-11-27T19:14:30",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 100,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-28T03:00:00/2026-11-28T03:31:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 5,
      "units": "%"
      }
    }
  ]
```
