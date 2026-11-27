<!-- ed813718-74f3-4776-aeac-b0c439161ddc -->
---
todos:
  - id: "branch"
    content: "Create the branch from main"
    status: completed
  - id: "files"
    content: "Rename the thirty-one storage entity and repository files with git mv"
    status: completed
  - id: "references"
    content: "Rewrite the type references across the dependent Java files"
    status: completed
  - id: "accessors"
    content: "Rename the storage accessors, session list field and entity-typed factory fields"
    status: completed
  - id: "tables"
    content: "Rename the five JPA storage tables"
    status: completed
  - id: "prose"
    content: "Update the javadoc that names the type, the Java test import, the Python docstring and AGENTS.md"
    status: completed
  - id: "build"
    content: "Compile and run the Java unit tests"
    status: completed
  - id: "suites"
    content: "Run the Python suites against a local broker"
    status: completed
  - id: "grep"
    content: "Confirm no old type or table name remains outside the generated Ivoa beans"
    status: completed
  - id: "commit"
    content: "Commit through bin/agent-commit after approval, push the fork and open the pull request"
    status: completed
isProject: false
---
# Plan: Rename the storage resources to storage reservations

Date: 2026-11-27
Scope: `Calycopis-broker` — the storage entity tiers, the accessors that expose
them, the session storage list, the entity-typed factory and repository fields,
five JPA tables, the Java test that imports the validator type, the Python
docstring that names the validator, and the storage data model in `AGENTS.md`.
The storage booking classes do not exist yet and are not renamed here.
Issues: [uksrc/Calycopis-broker#78](https://github.com/uksrc/Calycopis-broker/issues/78)
— storage resource pools, stage 1; [#169](https://github.com/uksrc/Calycopis-broker/issues/169)
— booking across resource types.
Parent: [20261125-01-storage-resource-pools-and-booking-across-resource-types](20261125-01-storage-resource-pools-and-booking-across-resource-types-059e5e37.plan.md).
Branch: `20261127-zrq-storage-reservations`, to be created from `main` at
`d1a6e4dc`.
Result: implemented on the branch, commit pending.

## Target

Rename the per-session storage component from a *storage resource* to a
*storage reservation*, in the classes, the accessors, the tables and every
reference. Nothing else changes: the same requests produce the same responses,
the kind URIs, bean types and HTTP paths are untouched, and the same rows come
back from the same columns under new table names.

This is stage 1 because every later stage names this type. Storage pools carry
reservations, allocations bound them, and the staged pipeline books them.
Renaming after those stages exist would mean editing all of that new code, and
the rename would then have a moving target.

It also settles the vocabulary before the new words arrive. A pool is a pool of
**storage resources**; a reservation is one booking of them; an allocation is
capacity granted to a pool. The parent plan's decision 1 records the rule: the
word "resource" is dropped where the type records a booking and kept where it
names the thing being pooled or requested.

## Where we are

The storage component is a three-tier entity, with mock and Docker
implementations:

| Tier | Files | Shape |
|---|---|---|
| Abstract | 8 | `AbstractStorageResource`, its entity, two factory types and four validator types |
| Simple | 6 | `SimpleStorageResource`, its entity, two factory types and two validator types |
| Mock | 6 | `MockSimpleStorageResource` and the same five files |
| Docker simple | 2 | `DockerSimpleStorageResourceEntityFactory` and its implementation |
| Docker bind | 4 | `DockerBindMountStorage`, its entity, its factory and factory implementation |
| Docker volume | 4 | `DockerVolumeMountStorage`, its entity, its factory and factory implementation |
| Spring repository | 1 | `SpringStorageResourceEntityRepository` |

Thirty-one files: thirty under
`java/src/main/java/net/ivoa/calycopis/broker/engine/entities/storage/`, plus
`spring/jpa/SpringStorageResourceEntityRepository.java`.

Eighty-five files under `java/src` mention `StorageResource`, 459 occurrences.
Of those, sixty-six are generated `IvoaAbstractStorageResource` and
`IvoaSimpleStorageResource` references that stay. The remaining references
include the entity packages, the data entity factories and linkers, the offer
set parser and context, the session, the volume mounts, the compute reservation
entity, the session processing requests, the platform interface and its two
implementations, the Spring repository and one Java test.

Five tables carry the names, declared in `@Table` annotations:

| Entity | Table |
|---|---|
| `AbstractStorageResourceEntity` | `abstractstorageresources` |
| `SimpleStorageResourceEntity` | `simplestorageresources` |
| `MockSimpleStorageResourceEntity` | `mocksimplestorageresources` |
| `DockerBindMountStorageEntity` | `dockerbindmountstorage` |
| `DockerVolumeMountStorageEntity` | `dockervolumemountstorage` |

There is no raw SQL storage booking query yet, so the table names are checked by
Hibernate at startup rather than by the compiler. The volume mount mapping uses
a `storageResource` field, a `storageresource` join column and a
`mappedBy = "storageResource"`; those names stay, following the compute rename
precedent, so the mapping does not change.

## The rename

Thirty-one type names, applied longest first so that a shorter name never eats a
longer one:

| Old | New |
|---|---|
| `AbstractStorageResourceEntityFactoryImpl` | `AbstractStorageReservationEntityFactoryImpl` |
| `AbstractStorageResourceValidatorFactoryImpl` | `AbstractStorageReservationValidatorFactoryImpl` |
| `DockerBindMountStorageEntityFactoryImpl` | `DockerBindMountStorageReservationEntityFactoryImpl` |
| `DockerVolumeMountStorageEntityFactoryImpl` | `DockerVolumeMountStorageReservationEntityFactoryImpl` |
| `DockerSimpleStorageResourceEntityFactoryImpl` | `DockerSimpleStorageReservationEntityFactoryImpl` |
| `MockSimpleStorageResourceEntityFactoryImpl` | `MockSimpleStorageReservationEntityFactoryImpl` |
| `MockSimpleStorageResourceValidatorImpl` | `MockSimpleStorageReservationValidatorImpl` |
| `SimpleStorageResourceEntityFactoryImpl` | `SimpleStorageReservationEntityFactoryImpl` |
| `SimpleStorageResourceValidatorImpl` | `SimpleStorageReservationValidatorImpl` |
| `AbstractStorageResourceEntityFactory` | `AbstractStorageReservationEntityFactory` |
| `AbstractStorageResourceValidatorFactory` | `AbstractStorageReservationValidatorFactory` |
| `DockerBindMountStorageEntityFactory` | `DockerBindMountStorageReservationEntityFactory` |
| `DockerVolumeMountStorageEntityFactory` | `DockerVolumeMountStorageReservationEntityFactory` |
| `DockerSimpleStorageResourceEntityFactory` | `DockerSimpleStorageReservationEntityFactory` |
| `MockSimpleStorageResourceEntityFactory` | `MockSimpleStorageReservationEntityFactory` |
| `MockSimpleStorageResourceValidator` | `MockSimpleStorageReservationValidator` |
| `SimpleStorageResourceEntityFactory` | `SimpleStorageReservationEntityFactory` |
| `SimpleStorageResourceValidator` | `SimpleStorageReservationValidator` |
| `AbstractStorageResourceValidatorImpl` | `AbstractStorageReservationValidatorImpl` |
| `AbstractStorageResourceEntity` | `AbstractStorageReservationEntity` |
| `AbstractStorageResourceValidator` | `AbstractStorageReservationValidator` |
| `MockSimpleStorageResourceEntity` | `MockSimpleStorageReservationEntity` |
| `SimpleStorageResourceEntity` | `SimpleStorageReservationEntity` |
| `SpringStorageResourceEntityRepository` | `SpringStorageReservationEntityRepository` |
| `DockerBindMountStorageEntity` | `DockerBindMountStorageReservationEntity` |
| `DockerVolumeMountStorageEntity` | `DockerVolumeMountStorageReservationEntity` |
| `MockSimpleStorageResource` | `MockSimpleStorageReservation` |
| `AbstractStorageResource` | `AbstractStorageReservation` |
| `DockerBindMountStorage` | `DockerBindMountStorageReservation` |
| `DockerVolumeMountStorage` | `DockerVolumeMountStorageReservation` |
| `SimpleStorageResource` | `SimpleStorageReservation` |

The accessors and fields that name the entity follow the type:

| Old | New |
|---|---|
| `getStorageResource` | `getStorageReservation` |
| `setStorageResource` | `setStorageReservation` |
| `getStorageResources` | `getStorageReservations` |
| `addStorageResource` | `addStorageReservation` |
| `storageresources` (session list field) | `storagereservations` |
| `storageResourceFactory` | `storageReservationFactory` |
| `abstractStorageResourceEntityFactory` | `abstractStorageReservationEntityFactory` |
| `abstractStorageResourceValidatorFactory` | `abstractStorageReservationValidatorFactory` |
| `dockerBindMountStorageResourceEntityFactory` | `dockerBindMountStorageReservationEntityFactory` |
| `dockerVolumeMountStorageResourceEntityFactory` | `dockerVolumeMountStorageReservationEntityFactory` |
| `mockStorageResourceEntityFactory` | `mockStorageReservationEntityFactory` |
| `springAbstractStorageResourceEntityRepository` | `springAbstractStorageReservationEntityRepository` |
| `springStorageResourceEntityRepository` | `springStorageReservationEntityRepository` |

Lower-case locals that hold an entity, the JPA field `storageResource`, the
`storageresource` join column and the `mappedBy = "storageResource"` stay. That
is deliberate: the mapping is a schema contract, and changing it is not part of
this stage. `getStorageResourceValidators` also stays, because it names the
validator factory in the platform interface rather than the booked entity.

Five tables, and the entity that declares each:

| Old table | New table |
|---|---|
| `abstractstorageresources` | `abstractstoragereservations` |
| `simplestorageresources` | `simplestoragereservations` |
| `mocksimplestorageresources` | `mocksimplestoragereservations` |
| `dockerbindmountstorage` | `dockerbindmountstoragereservations` |
| `dockervolumemountstorage` | `dockervolumemountstoragereservations` |

The `@DiscriminatorValue` kind URIs stay unchanged, including
`uri:mock-simple-storage-resource`, `uri:docker-bind-storage` and
`uri:docker-volume-storage`. The `KIND_DISCRIMINATOR` URI for
`simple-storage-resource.yaml` also stays.

## What does not change

* The bean types `IvoaAbstractStorageResource` and `IvoaSimpleStorageResource`,
  the kind discriminators, `KIND_DISCRIMINATOR`, `WEBAPP_PATH`, the HTTP paths
  and every request and response field.
* The generated Python client's `SimpleStorageResource` and the tests that
  construct it. The schema vocabulary keeps the word "resource".
* The storage linkers: `AbstractStorageLinker`, `DockerStorageLinker` and
  `DockerStorageLinkerImpl`.
* The volume mount interface `DockerVolumeMount` and the volume mount types.
  The Docker bind and volume storage reservations still implement
  `DockerVolumeMount`.
* The data resource `getStorage()`/`setStorage()` accessors and the `storage`
  join column, which name the link rather than the entity type.
* The platform methods `getStorageResourceValidators()`, and the configuration
  keys.
* The storage booking classes, which do not exist yet. Stage 5 and stage 6 add
  and then rename them while they rewrite the query.
* The API vocabulary in messages and responses. "Storage resource" stays where
  it names the schema concept; the class names change.

## Method

A scripted rename over an explicit identifier list, matched whole. That is the
safety property this plan depends on: `SimpleStorageResource` is a prefix of
`SimpleStorageResourceEntity`, of `SimpleStorageResourceValidator` and of the
generated `IvoaSimpleStorageResource` bean, so a replacement that matches
substrings corrupts files this plan keeps out of scope. Matched as a whole word
it does not: there is no word boundary before `Simple` in
`IvoaSimpleStorageResource`, nor after `Resource` in
`SimpleStorageResourceEntity`, and `getStorageResourceValidators` is a different
word from `getStorageResource`. Ordering the list longest first is belt and
braces.

1. Create the branch from `main` at `d1a6e4dc`.
2. Rename the thirty-one files with `git mv`, so the history follows them.
3. Apply the type table to every file the compiler needs, then compile. The
   compiler is the oracle for the reference list, not a grep.
4. Apply the accessor and field table by hand, reviewing each hit, because the
   same names appear on the session interface and entity, the volume mounts,
   the data linkers and the session processing requests.
5. Rename the five tables in the `@Table` annotations. Keep the
   `@DiscriminatorValue` URIs unchanged. There is no raw SQL storage query to
   update in this stage.
6. Update the Java test import of `AbstractStorageResourceValidator`, the
   Python docstring that names `MockSimpleStorageResourceValidatorImpl`, and
   the storage data model in `AGENTS.md`.
7. Compile, run the tests, and grep for the old type and table names.

Keep the change in one commit. A half-renamed tree does not compile, so there
is no useful intermediate state to review or bisect.

## Verification

```bash
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
schema at startup, so the renamed tables appear on the next start and no
migration is involved. `any` and `mock` run against a mock broker; `states` and
`docker` need a Docker broker and may need the capacity and delay settings the
existing suites expect.

```bash
# No old Java type names, excluding the generated Ivoa beans.
grep -rnwE \
    'AbstractStorageResource|SimpleStorageResource|MockSimpleStorageResource|DockerSimpleStorageResourceEntityFactory|SpringStorageResourceEntityRepository' \
    java/src

# No old Docker bind or volume storage type names.
grep -rnwE 'DockerBindMountStorage|DockerVolumeMountStorage' java/src

# No old table names, matched as quoted @Table values.
grep -rnE \
    'name = "(abstractstorageresources|simplestorageresources|mocksimplestorageresources|dockerbindmountstorage|dockervolumemountstorage)"' \
    java/src
```

## Acceptance

* `./mvnw clean compile` is green and `./mvnw test` passes, with the Java tests
  changed only where a name changed.
* The `any`, `mock`, `states` and `docker` Python suites pass on the platforms
  they need. A validated request returns the same session shape, the same kind
  and the same numbers as before.
* The greps above return no old type or table names outside the generated
  `IvoaAbstractStorageResource` and `IvoaSimpleStorageResource` beans.
* The broker starts against PostgreSQL and Hibernate creates the five renamed
  tables. No SQL error refers to a missing storage table.
* No OpenAPI, response, path or configuration change. `git diff` over
  `src/main/resources` is empty.
* One commit. `git diff --stat` touches the thirty-one renamed files and the
  files that reference them, and nothing else.

## Risks

* **The prefix collision.** `SimpleStorageResource` appears inside names this
  plan keeps, including the generated `Ivoa` beans. Whole-word matching is what
  stops a substitution reaching them; a quick `sed s/Resource/Reservation/`, or
  a regex without the boundaries, would break the beans and the volume mounts in
  one pass, and would look plausible in the diff.
* **The JPA mapping.** The tables change but the `storageResource` field, the
  `storageresource` join column and the `mappedBy = "storageResource"` stay.
  Changing one without the others would break the volume mount join at startup.
  The plan deliberately keeps them all.
* **The Docker bind and volume types.** They are both storage reservations and
  volume mounts, so their rename touches the volume mount package. The compiler
  finds the references, but read the diff of `AbstractVolumeMountEntity`, the
  simple volume mounts and the two Docker storage entities rather than skimming
  it.
* **The tables are runtime-checked.** There is no raw SQL storage query to catch
  a missed table name at compile time. The broker startup and the Python suites
  are what catch it; do not skip them because the Java tests are green.
* **The javadoc.** Prose that says "storage resource" stays where it means the
  API concept and changes where it names the class. That is a judgement call per
  sentence, so read the diff of the storage interfaces, the data linkers, the
  session and the volume mounts.
* **The generated beans.** `IvoaAbstractStorageResource` and
  `IvoaSimpleStorageResource` are schema types, not internal entity types. They
  keep their names, and so does the Python client's `SimpleStorageResource`.

## Out of scope

Storage pools, allocations, policies, storage classes, booking and capacity.
The storage booking classes, which stage 5 and stage 6 add. The schema
vocabulary, which keeps the word "resource". The `DockerVolumeMount` interface,
the data resource link, and the generated `Ivoa` beans. The configuration keys.

## Status

status: implemented
details: Implemented in commit [`d5431b5e`](https://github.com/Zarquan/Calycopis-broker-uksrc/commit/d5431b5e)
  and opened as [PR #234](https://github.com/uksrc/Calycopis-broker/pull/234).
  Java compile and 105 unit tests pass. The Python any suite passed 106 tests,
  the mock suite passed 56, docker+any passed 169, and states passed 6 with 3
  skipped. PostgreSQL created the five renamed tables.

## AIMetrics

```text
AIMetrics: [
    {
    "timestamp": "2026-11-27T17:10:21",
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
