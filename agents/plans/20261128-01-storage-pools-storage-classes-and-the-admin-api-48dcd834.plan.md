<!-- 48dcd834-7283-4287-b20a-7d892fb1c875 -->
---
todos:
  - id: "branch"
    content: "Create the branch from main at aacee00d"
    status: completed
  - id: "plan-records"
    content: "Record the merged Stage 2 plan status and update the parent plan"
    status: completed
  - id: "class"
    content: "Add StorageClass with VOLUME and BIND"
    status: completed
  - id: "entities"
    content: "Add the storage pool entity tiers and their JPA tables"
    status: completed
  - id: "factory"
    content: "Add the storage pool factory, repository and Spring wiring"
    status: completed
  - id: "settings"
    content: "Extend the pools configuration binding with the storage list"
    status: completed
  - id: "bootstrap"
    content: "Seed default-volume, default-bind and configured storage pools idempotently"
    status: completed
  - id: "admin"
    content: "Add POST, list and select storage pool admin endpoints"
    status: completed
  - id: "config"
    content: "Add the storage examples and comments to config/pools.yaml"
    status: completed
  - id: "tests"
    content: "Add Java unit tests and the Python admin storage pool tests"
    status: completed
  - id: "build"
    content: "Compile and run the Java unit tests"
    status: completed
  - id: "suites"
    content: "Run the Python any, mock and docker suites against a local broker"
    status: completed
  - id: "commit"
    content: "Commit through bin/agent-commit after approval, push the fork and open the pull request"
    status: pending
isProject: false
---
# Plan: Storage pools, storage classes and the admin API

Date: 2026-11-28
Scope: `Calycopis-broker` — a new storage pool entity family, the storage class
enum, a Spring repository, a startup seeder, the storage section of the pools
configuration, three admin endpoints, and the Java and Python tests for them.
Booking, offers, capacity, reservations and the published schema are untouched.
Issues: [uksrc/Calycopis-broker#78](https://github.com/uksrc/Calycopis-broker/issues/78)
— storage resource pools, stage 3; [#169](https://github.com/uksrc/Calycopis-broker/issues/169)
— booking across resource types.
Parent: [20261125-01-storage-resource-pools-and-booking-across-resource-types](20261125-01-storage-resource-pools-and-booking-across-resource-types-059e5e37.plan.md).
Builds on: [20261127-02-storage-reservations-record-size-and-hold-the-compute-window](20261127-02-storage-reservations-record-size-and-hold-the-compute-window-0c2eeee5.plan.md),
merged as [PR #235](https://github.com/uksrc/Calycopis-broker/pull/235).
Branch: `20261128-zrq-storage-pools-and-admin-api`, to be created from `main` at
`aacee00d`.
Result: plan only. No implementation or commit yet.

## Target

A storage pool is a persisted, admin-managed entity that serves exactly one
storage class. A fresh deployment gets a volume pool and a bind pool for free,
an administrator can create and read further pools, and a deployment can name
its own pools in the storage section of `pools.yaml`.

Nothing in the booking path reads a storage pool after this stage. The storage
reservation keeps the size and window it gained in stage 2, and it does not yet
name a pool or a class. The existing offer generation and storage lifecycle
behaviour must be byte-for-byte unchanged.

This is stage 3 because later stages attach to the noun. Stage 4 gives a pool
allocations and capacity, stage 5 makes each reservation name its pool and
class, stage 6 moves storage offer generation inside the pool, and stage 7 adds
ordering and policies. Each of those needs a pool that already exists.

## Where we are

Stage 1, the rename from storage resources to storage reservations, is merged in
PR #234 at `3224d127`. Stage 2, recording the requested and offered size and
deriving the storage window from the compute reservation, is merged in PR #235
at `aacee00d`; its branch commit is `9147d162`. Local `main`, `origin/main` and
`uksrc/main` all point at `aacee00d`.

The relevant existing machinery:

* `AbstractStorageReservationEntity` and `SimpleStorageReservationEntity` hold
  the booked storage, including the requested/offered min/max size and the
  compute-derived hull. There is no pool column and no class column.
* The compute pool tier is the precedent: `AbstractComputeResourcePool`,
  `SimpleComputeResourcePool`, their joined-inheritance entities, the engine
  factory/repository interfaces and the Spring repository and factory wiring.
  `AdminComputePoolsController` provides the create, list and select pattern
  under `/admin/pools/compute`.
* `ComponentEntity` supplies `uuid`, `name`, `description`, `created`,
  `modified`, `owner` and the message list. A pool extends `ComponentEntity`
  rather than `LifecycleComponentEntity`: it has no phase or schedule.
* `SecurityConfig` already maps `/admin/**` to the `ADMIN` authority before the
  blanket GET rule. No security matcher change is needed for this stage.
* `PoolsSettingsImpl` binds `calycopis.broker.pools` and carries only the compute
  list. `ComputePoolBootstrap` seeds compute pools only. `config/pools.yaml`
  contains compute examples only, and `bin/config-broker.sh` writes a compute-only
  `pools.yaml` into a fresh configuration volume.
* There is no `StorageClass`, no storage pool table, no storage pool factory,
  no storage bootstrap and no `/admin/pools/storage` endpoint.

The Stage 2 plan record still says "Commit pending approval" and its `commit`
todo is still pending, because the second plan-record commit was missed. This
stage carries that correction.

## Design

### Storage class

Add `StorageClass` as a small enum in
`engine/entities/storage/pool/StorageClass.java`:

```java
public enum StorageClass
    {
    VOLUME,
    BIND;

    public static final StorageClass DEFAULT = VOLUME;
    }
```

It also gets a parser that trims, upper-cases with `Locale.ROOT`, and returns an
`Optional<StorageClass>`. A blank or null value is not parsed; the caller decides
whether blank means the default. `VOLUME` is the default for an admin create
without a class and for a configured pool without a `storageClass` value.
`BIND` is for host-path-backed storage. The mock platform's simple storage uses
`VOLUME` because it is broker-managed, but that choice is not made in this stage:
the class is not yet attached to any reservation.

The class is an admin and configuration field, not a schema field. It does not
appear in the OpenAPI schema, the storage request, the storage response or any
published type.

### Pool entity tier

Follow the compute pool shape exactly, with storage names:

| File | Role |
|---|---|
| `engine/entities/storage/pool/AbstractStorageResourcePool.java` | Interface extending `Component`, with `getPriority()`, `isEnabled()` and `getStorageClass()`, plus the two default pool names. |
| `engine/entities/storage/pool/AbstractStorageResourcePoolEntity.java` | Abstract `@Entity`, `@Inheritance(JOINED)`, `@Table(name = "abstractstorageresourcepools")`; holds priority, enabled and the `StorageClass` column. |
| `engine/entities/storage/pool/simple/SimpleStorageResourcePool.java` | Interface extending `AbstractStorageResourcePool`, with `KIND_DISCRIMINATOR = uri:storage-pool`. |
| `engine/entities/storage/pool/simple/SimpleStorageResourcePoolEntity.java` | Concrete `@Entity`, `@DiscriminatorValue("uri:storage-pool")`, `@Table(name = "simplestorageresourcepools")`. |
| `engine/entities/storage/pool/simple/SimpleStorageResourcePoolEntityFactory.java` | Factory interface. |
| `engine/entities/storage/pool/simple/SimpleStorageResourcePoolEntityFactoryImpl.java` | Framework-neutral factory implementation. |
| `engine/entities/storage/pool/simple/SimpleStorageResourcePoolEntityRepository.java` | Framework-neutral repository interface. |
| `spring/jpa/SpringStorageResourcePoolEntityRepository.java` | Spring Data repository extending the engine interface. |
| `spring/pools/SpringSimpleStorageResourcePoolEntityFactoryImpl.java` | `@Component` subclass supplying the repository. |

The abstract table carries `priority`, `enabled` and `storageclass`. The concrete
table carries the discriminator join. The entity uses
`@Enumerated(EnumType.STRING)` for `storageclass`, with a non-null column and an
in-memory default of `StorageClass.VOLUME` for the JPA no-arg constructor.

`AbstractStorageResourcePoolEntity.getWebappPath()` returns `/pools/storage/`.
The simple entity's `getKind()` returns the discriminator URI. The kind is
off-schema, like `uri:compute-pool` and `uri:identity`.

The abstract interface declares the two default names:

```java
public static final String DEFAULT_VOLUME_POOL_NAME = "default-volume";
public static final String DEFAULT_BIND_POOL_NAME = "default-bind";
```

The class accessor lives on the storage-specific interface, not on a future
generic resource pool interface. Stage 10 can move the generic parts later, but
the class belongs to storage and must not be forced onto compute pools.

The factory supports:

* `select(UUID)`;
* `findByName(String)`, the bootstrap's idempotency key;
* `list()`;
* `create(name, description, StorageClass)`, for the admin create, with priority
  `0` and enabled `true`;
* `create(name, description, int priority, boolean enabled, StorageClass)`, for
  the bootstrap.

It does not add an update method or a delete method in this stage. The compute
factory has an update because the compute admin API has an update verb; storage
pool priority and enabled become editable in stage 7, together with policies.
Adding a storage update method now would leave it unused and would invite an
endpoint this stage does not specify.

The repository mirrors the compute repository: `findByName`, `findAll` and the
Spring Data base. Queries by enabled flag and storage class arrive with stage 7,
where the offer path needs them; nothing in this stage reads them.

### Configuration binding and bootstrap

The existing settings type is compute-specific. Rename it to a pools-level
binding that carries both lists:

| Existing file | New file |
|---|---|
| `spring/pools/ComputePoolsSettings.java` | `spring/pools/PoolsSettings.java` |
| `spring/pools/ComputePoolsSettingsImpl.java` | `spring/pools/PoolsSettingsImpl.java` |

`PoolsSettings` gets `getCompute()` and `getStorage()`. `PoolsSettingsImpl` keeps
the same `@ConfigurationProperties(prefix = "calycopis.broker.pools")` and binds
`List<StoragePoolSpec> storage` alongside `List<ComputePoolSpec> compute`.
`ComputePoolBootstrap` switches its constructor and field type to `PoolsSettings`;
its behaviour and its tests are otherwise unchanged. This is a mechanical rename
with no YAML change for compute.

Add `spring/pools/StoragePoolSpec.java` with `name`, `description`, `priority`,
`enabled` and `storageClass`. The class is a `String` in the spec rather than an
enum, so the bootstrap can distinguish an omitted class from an invalid one and
log a useful error without Spring failing the whole application at bind time.

Add `spring/pools/StoragePoolBootstrap.java` as an `ApplicationRunner`. It
seeds in this order and with these rules:

1. Build the default specs first: `default-volume` with `VOLUME`, and
   `default-bind` with `BIND`, both priority `0`, enabled `true`, with a short
   description.
2. Add each configured storage spec in order.
3. A configured spec with a null or blank name is logged and skipped.
4. A configured spec named `default-volume` or `default-bind` may override the
   description, priority and enabled flag, but its class is forced to the
   default class. A conflicting configured class is logged as a warning. This
   keeps the invariant that the two default pools always have the class their
   names promise.
5. A configured non-default spec with no class defaults to `VOLUME`.
6. A configured non-default spec with an unknown class is logged as an error and
   skipped. A bad class must not stop the broker, but it also must not create a
   pool that cannot serve a known class.
7. Duplicate configured names are logged and skipped after the first. The
   defaults win over a duplicate configured default.
8. For each resolved spec, look the pool up by name; if it exists, leave it
   alone, otherwise create it. A second bootstrap run against the same database
   therefore creates no second row.
9. A negative configured priority is logged and treated as `0`, matching the
   compute bootstrap's handling.

Both defaults are always resolved, even when `pools.yaml` names other storage
pools or has no storage section at all. This is the parent plan's requirement
and is what makes a fresh deployment work without a storage configuration.

### Admin API

Add `spring/webapp/AdminStoragePoolsController.java` under
`/admin/pools/storage`:

| Verb | Path | Request | Response |
|---|---|---|---|
| `POST` | `/admin/pools/storage` | `{"name": ..., "description": ..., "storageClass": "VOLUME"|"BIND"}` | `201 Created`, `Location: /admin/pools/storage/{uuid}`, pool body |
| `GET` | `/admin/pools/storage` | — | `200 OK`, array of pool bodies |
| `GET` | `/admin/pools/storage/{uuid}` | — | `200 OK`, pool body, or `404 Not Found` |

`StoragePoolRequest` is a hand-written record with `name`, `description` and
`storageClass`. `StoragePoolResponse` is a hand-written record with `uuid`,
`name`, `description`, `priority`, `enabled`, `storageClass` and `created`. The
class is serialized as its enum name, `VOLUME` or `BIND`. The response has no
`modified` timestamp, for the same reason as the compute pool response: nothing
maintains it.

Create rules:

* A null, empty or whitespace-only name is `400`.
* The name is trimmed before the duplicate check and before it is stored.
* A duplicate name is `409`, across both classes.
* A null or blank `storageClass` means `VOLUME`.
* A non-blank class is parsed case-insensitively; an unknown value is `400`.
* A new pool is created with priority `0` and enabled `true`. Stage 7 adds the
  update verb that changes those fields.

Select rules:

* The identifier arrives as a string, so a malformed UUID is a `400` rather than
  a Spring type mismatch that becomes a `500`.
* An unknown UUID is `404`.

Add three storage-specific problem types to `ProblemType` rather than reusing the
compute titles:

* `INVALID_STORAGE_POOL`, `400`, slug `invalid-storage-pool`;
* `STORAGE_POOL_NOT_FOUND`, `404`, slug `storage-pool-not-found`;
* `STORAGE_POOL_NAME_IN_USE`, `409`, slug `storage-pool-name-in-use`.

`SecurityConfig` needs no change. `/admin/**` already requires `ADMIN`, and the
compute admin tests already cover the matcher order. The storage tests repeat the
`401` and `403` checks so a future matcher edit cannot silently expose the new
list endpoint.

### Configuration example

Extend `config/pools.yaml` with a commented storage section containing the two
defaults:

```yaml
        storage:
          -
            name: default-volume
            description: The default volume storage pool.
            storageClass: VOLUME
            priority: 0
            enabled: true
          -
            name: default-bind
            description: The default bind storage pool.
            storageClass: BIND
            priority: 0
            enabled: true
```

The surrounding comment explains that a pool serves one class, that omitted
classes default to `VOLUME`, that unknown classes are skipped at startup, and
that both defaults are seeded whether or not the section names them.

`bin/config-broker.sh` stays compute-only. A fresh deployment therefore exercises
the no-storage-section path and gets both defaults from the bootstrap, which is
the behaviour the parent plan requires. Updating the generated file to repeat
the defaults would duplicate the bootstrap's responsibility.

### What does not change

* No OpenAPI schema, generated client, bean type, HTTP storage path or response
  field changes. The class is not published.
* No storage reservation column, table, size, window, lifecycle rule or bean
  change.
* No reservation-to-pool join and no class on a reservation. That is stage 5.
* No storage allocation, capacity query, policy, offer factory or booking
  pipeline change. Those are stages 4, 6, 7 and 8.
* No change to the compute pool API, the compute bootstrap's seeding rules or
  the compute `pools.yaml` binding.
* No security matcher change.

## Deferred Stage 2 plan record

The second Stage 2 plan-record commit was missed. Include these documentation
changes in the same changeset as Stage 3:

* `agents/plans/20261127-02-storage-reservations-record-size-and-hold-the-compute-window-0c2eeee5.plan.md`:
  set the YAML `commit` todo to `completed`; change `## Status` to record commit
  `9147d162` and PR #235 merged at `aacee00d`; add an AIMetrics interval for the
  record edit.
* `agents/plans/20261125-01-storage-resource-pools-and-booking-across-resource-types-059e5e37.plan.md`:
  set the `stage-3` todo to `in_progress`; update `details` to record Stage 2
  merged in PR #235 at `aacee00d` and Stage 3 planned in this file; add an
  AIMetrics interval for the record edit.

No Stage 2 code changes belong in this changeset. The correction is limited to
the plan records and their metrics.

## Changes

### Java

* Add `StorageClass`.
* Add the storage pool interface/entity tiers, factory, repository and Spring
  wiring.
* Rename the pools settings to `PoolsSettings`/`PoolsSettingsImpl` and add the
  storage list.
* Add `StoragePoolSpec` and `StoragePoolBootstrap`.
* Add `AdminStoragePoolsController`, `StoragePoolRequest` and
  `StoragePoolResponse`.
* Add the three storage problem types.
* Update `ComputePoolBootstrap` for the settings rename.

### Configuration

* Add the storage examples and comments to `config/pools.yaml`.
* Do not change `application.yaml`, `bin/config-broker.sh`, `SecurityConfig` or
  the storage reservation configuration.

### Tests

Java:

* `StorageClassTest`: `VOLUME` and `BIND` parse, lower case parses, null and
  unknown values are empty, and `DEFAULT` is `VOLUME`.
* `StoragePoolBootstrapTest`: both defaults are seeded; the seed is idempotent;
  a configured pool gets its class; a configured default keeps its promised
  class; defaults are added when other pools are configured; a missing class
  defaults to `VOLUME`; an invalid class is skipped; duplicate names produce one
  pool; a negative priority becomes `0`.
* `PoolsSettingsImplTest` (renamed from `ComputePoolsSettingsImplTest`): both
  lists default to empty; compute and storage entries bind with their fields;
  the existing compute resolve tests still pass.

Python:

* Add `tests/python/any/test_admin_storage_pools.py`, using the same raw
  `urllib` and basic-auth helpers as `test_admin_pools.py`. It asserts:
  * admin create returns `201`, a `Location` header and the given class;
  * create without a class returns `VOLUME`;
  * a lower-case class is accepted and normalized;
  * an invalid class returns `400`;
  * duplicate name returns `409`;
  * blank/missing name returns `400`;
  * the list contains `default-volume` and `default-bind` with the correct
  classes;
  * select by UUID returns the pool, an unknown UUID returns `404`, and a
  malformed UUID returns `400`;
  * all three verbs return `401` without credentials and `403` for a local
  identity.
* Leave `tests/python/any/test_admin_pools.py` unchanged.

### Plan records

* Add this Stage 3 plan.
* Apply the deferred Stage 2 record corrections above.

## Method

1. Create `20261128-zrq-storage-pools-and-admin-api` from `main` at `aacee00d`.
2. Apply the deferred Stage 2 plan-record corrections and mark the parent
   `stage-3` todo in progress.
3. Add `StorageClass` and the storage pool entity tier with the two joined
   tables.
4. Add the factory, repository and Spring wiring.
5. Rename the settings binding to `PoolsSettings`/`PoolsSettingsImpl`, add
   `StoragePoolSpec`, and switch `ComputePoolBootstrap` to the new type.
6. Add `StoragePoolBootstrap` and its default/configured seeding rules.
7. Add the admin controller, request/response records and problem types.
8. Add the storage section to `config/pools.yaml`.
9. Add the Java unit tests and the Python `any` test.
10. Compile and run the Java tests; run the Python `any`, `mock` and `docker`
    suites against a broker built from the branch.
11. Inspect the diff against `main` to confirm no OpenAPI, reservation, offer or
    lifecycle files changed.
12. After explicit approval of the exact change set and commit message, commit
    through `bin/agent-commit`, push the fork over HTTPS and open the pull
    request. The commit and PR bodies end with an `AIMetrics` block using an
    `interval`.

## Verification

Java, from `java/`:

```bash
cd /Calycopis/Calycopis-broker/java
source ../bin/versions.sh ../config.yaml
MAVEN_USER_HOME=/Calycopis/agents/scratch/m2 \
  ./mvnw -q -Dmaven.repo.local=/Calycopis/agents/scratch/m2/repository test
```

Expected: compile is clean and the full unit suite passes, including the new
storage class, bootstrap and settings tests.

Python, against a broker built from the branch:

* Run the `any` suite; it includes the new admin storage pool test and must
  continue to pass the existing compute admin tests.
* Run the `mock` and `docker` suites to confirm the new joined tables do not
  disturb either platform. The stage does not change storage behaviour, so the
  storage registration, bind/volume and lifecycle tests must be unchanged.
* If a full state-suite run is too slow because of the session-expiry tests, run
  the storage-specific state test and the admin test directly, and record the
  partial run honestly.

SQL check after startup: `abstractstorageresourcepools` and
`simplestorageresourcepools` exist, and the storage pool table has one row per
default plus one per configured name. The storage reservation tables must be
unchanged.

Diff check:

```bash
git diff --stat main
git diff --exit-code main -- \
  openapi \
  java/src/main/resources \
  java/src/main/java/net/ivoa/calycopis/broker/engine/entities/storage/simple \
  java/src/main/java/net/ivoa/calycopis/broker/engine/functional/booking
```

The OpenAPI, resources and storage reservation paths must show no changes. The
booking path must show no changes. The only storage changes are the new pool
package and the bootstrap/admin/config files.

## Acceptance

* An admin can create a storage pool, list storage pools and select one by UUID;
  a request with no credentials is `401`, and a local identity is `403`.
* Both `default-volume` (class `VOLUME`) and `default-bind` (class `BIND`) exist
  after startup, whether or not `pools.yaml` has a storage section.
* Running the bootstrap twice against the same database creates one pool per
  configured name.
* A pool response carries UUID, name, description, priority, enabled flag and
  storage class.
* A create without a class returns `VOLUME`; a create with an unknown class
  returns `400`.
* A duplicate pool name returns `409`; a malformed UUID returns `400`; an
  unknown UUID returns `404`.
* A configured pool can name its class; an omitted class becomes `VOLUME`; an
  unknown configured class is skipped with an error and does not prevent the
  defaults from being seeded.
* Offer generation and storage booking behaviour are unchanged; no OpenAPI
  source, storage reservation, offer factory or lifecycle rule changes.
* Java compile and unit tests pass; the Python `any`, `mock` and `docker` suites
  pass.
* The deferred Stage 2 plan record has its commit, PR and status recorded, and
  the parent plan records that Stage 2 is merged.

## Risks

* **The settings rename reaches compute code.** `ComputePoolBootstrap` and its
  tests must move to `PoolsSettings`. The compute binding tests are the guard
  that the rename did not change the compute YAML path. Keep the change
  mechanical and do not alter compute seeding rules.
* **Default names carry a class promise.** A configured `default-volume` with
  class `BIND` would break the invariant. The bootstrap forces the default
  class and logs the conflict. Do not let a configuration override it.
* **An invalid configured class has no valid pool to create.** Skipping it is
  deliberate; the defaults still exist. Do not silently substitute `VOLUME`,
  because that would make a typo look like a working bind pool.
* **The joined table.** `storageclass` lives on
  `abstractstorageresourcepools`, while the concrete discriminator row lives on
  `simplestorageresourcepools`. A query that reads only the simple table will
  not see the class. Read through the entity or join the abstract table.
* **Check-then-act duplicate names.** Two admin creates racing on the same name
  can both pass `findByName` and insert. The endpoint is admin-only, and a
  unique constraint is deferred with the update verb in stage 7. Record the
  limitation rather than pretending it is closed.
* **Admin create leaves priority and enabled fixed.** That is intentional:
  stage 7 owns the update verb. Do not add a storage update endpoint here just
  because compute has one.
* **No booking path may read the pool.** It is tempting to join reservations to
  pools while adding the entity. That is stage 5 and would change storage
  behaviour in a stage whose acceptance is that nothing changes.
* **`bin/config-broker.sh` stays compute-only.** A fresh deployment must exercise
  the fallback path. If the generated file is changed to list storage pools, the
  fallback is no longer tested by deployment.
* **A restart keeps nothing.** `ddl-auto: create` recreates the schema, so
  admin-created pools and their UUIDs disappear on restart and the bootstrap
  recreates the defaults by name. This is existing project behaviour, not a
  stage defect, but it makes pool UUIDs unsuitable for durable references.

## Out of scope

Storage allocations and capacity over time (stage 4). The reservation-to-pool
and reservation-to-class join (stage 5). `makeOffers`, storage criteria and the
storage booking query (stage 6). Pool ordering, enabled-by-class queries, policies
and their admin endpoints (stage 7). The staged compute-and-storage pipeline
(stage 8). Confirmation, release and concurrency hardening (stage 9). The generic
resource pool abstraction (stage 10). Documentation, worked configuration
examples and final hardening (stage 11). Any OpenAPI or client change.

## Status

status: implemented
details: Implemented on branch `20261128-zrq-storage-pools-and-admin-api` from
  `main` at `aacee00d`. Java compile and all 121 unit tests pass. The Python
  `docker any` suites passed 186 tests, the `mock any` suites passed 180, and
  the new admin storage pool tests passed 17. The changeset includes the
  deferred Stage 2 plan-record correction. Commit pending approval.

## AIMetrics

```text
AIMetrics: [
    {
    "interval": "2026-11-28T02:45:00/2026-11-28T02:58:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 100,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-28T02:58:00/2026-11-28T03:31:00",
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
