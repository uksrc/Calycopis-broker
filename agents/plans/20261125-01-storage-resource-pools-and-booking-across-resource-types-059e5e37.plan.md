<!-- 059e5e37-e1b0-465c-a130-9e60e62ce632 -->
---
todos:
  - id: "stage-1"
    content: "Stage 1: rename storage resources to storage reservations"
    status: completed
  - id: "stage-2"
    content: "Stage 2: storage reservations record size and hold the compute window"
    status: completed
  - id: "stage-3"
    content: "Stage 3: storage pools, storage classes and the admin API"
    status: in-progress
  - id: "stage-4"
    content: "Stage 4: storage allocations and capacity over time"
    status: pending
  - id: "stage-5"
    content: "Stage 5: storage reservations join their pool and class"
    status: pending
  - id: "stage-6"
    content: "Stage 6: the storage pool makes storage offers"
    status: pending
  - id: "stage-7"
    content: "Stage 7: several storage pools, each with its own policy"
    status: pending
  - id: "stage-8"
    content: "Stage 8: the provisional compute-and-storage pipeline"
    status: pending
  - id: "stage-9"
    content: "Stage 9: confirmation, release and concurrency hardening"
    status: pending
  - id: "stage-10"
    content: "Stage 10: generic resource pool abstraction"
    status: pending
  - id: "stage-11"
    content: "Stage 11: documentation, configuration examples and hardening"
    status: pending
  - id: "agree"
    content: "Agree the decisions and open questions"
    status: pending
isProject: true
---
# Plan: Storage resource pools and booking across resource types

Date: 2026-11-25
Scope: `Calycopis-broker` — the storage entity tier, storage pool/allocation/policy entities, the storage booking query and offer factory, the staged compute-and-storage booking pipeline, the admin API, configuration, Java and Python tests, and the documentation.
Issues: [uksrc/Calycopis-broker#169](https://github.com/uksrc/Calycopis-broker/issues/169) — booking across resource types; [#78](https://github.com/uksrc/Calycopis-broker/issues/78) — storage resource pools; [#216](https://github.com/uksrc/Calycopis-broker/issues/216) — generic resource pool abstraction. Related: [#74](https://github.com/uksrc/Calycopis-broker/issues/74) — storage resource reservations; [#72](https://github.com/uksrc/Calycopis-broker/issues/72) — re-usable booking component.
Builds on: [20261114-03-compute-resource-pools-and-allocations](20261114-03-compute-resource-pools-and-allocations-036b0f4f.plan.md), implemented across stages 1–7.
Deliverable: `agents/plans/20261125-01-storage-resource-pools-and-booking-across-resource-types-059e5e37.plan.md` (UUID `059e5e37-e1b0-465c-a130-9e60e62ce632`). Printable copy: `agents/plans/20261125-01-storage-resource-pools-and-booking-across-resource-types-059e5e37.plan.pdf`.

## Target

Every storage resource a session holds is a reservation allocated from a storage
pool, and the broker books compute and storage through a staged pipeline:

1. generate a provisional set of compute offers, and commit their reservations;
2. generate provisional storage reservations matched to each compute offer;
3. select the complete sets, confirm them, and release the rest;
4. return the confirmed sets as the offer response.

Compute and storage stay separate booking problems. Each resource type owns its
capacity query, its pools and its reservations. The pipeline composes them; it
does not become one query and does not run in one long transaction.

Success means:

* storage pools, allocations and policies exist and are administered through
  `/admin/pools/storage`;
* storage capacity is enforced per pool, per block, from allocations over time;
* storage results carry a storage class, and volume-mounted and bind-mounted
  storage are booked from their own default pools;
* a request with storage returns offers whose storage reservations come from
  storage pools, with storage timing ordered against data and compute;
* concurrent compute and storage requests cannot over-commit either pool;
* compute-only behaviour is unchanged;
* after storage works, compute and storage pools share one generic abstraction
  (#216) without changing the public API, admin paths or configuration;
* the model and configuration are documented.

## Where we are

* The compute pool model is complete. Compute pools, allocations, policies,
  admin endpoints, startup seeding, pool-scoped booking queries, Prometheus
  metrics and the staged offer factory are all on `main`. The compute plan’s
  seven stages are implemented.
* Storage is still a session lifecycle component only. `AbstractStorageResource`
  and `SimpleStorageResourceEntity` record preparation, availability and release
  windows, data resources and volume mounts. The validator validates the
  schema’s `SimpleStorageSize` (min/max GiB), but the entity constructor still
  carries `// TODO Add the storage fields ...`; the requested size is not
  persisted.
* The storage and data windows are anchored at the epoch rather than the offered
  window. Storage derives its preparation start from
  `session.getPrepareStartInstantSeconds()`, but the session’s lifecycle timing
  is never initialised on the offer path (`init` is commented out), so the
  session fields remain zero. Data resources inherit the storage window. This
  is tolerable while storage is not booked; it is not tolerable for a storage
  capacity query.
* Storage resources are created in two ways. An explicit request is validated by
  the platform’s storage validator. A data resource with no storage reference
  gets a storage resource auto-created by the data-storage linker; the docker
  linker creates bind-mount or volume storage directly, bypassing the storage
  validator, and therefore without a size.
* The docker data linker already selects the storage implementation from the
  data URL: `file://` data becomes bind-mount storage, `http(s)` data becomes
  volume-mount storage, and explicit storage uses the volume factory. On mock,
  explicit and auto-created storage use the simple storage factory. The plan
  makes this distinction explicit as a storage class rather than leaving it
  implicit in the linker.
* Booking is one transaction today. `OfferSetEntityFactoryImpl.create` wraps
  `stageTwo` in `BookingTransactionHandler.book` under the compute resource
  type, and `stageTwo` builds compute, storage, data, volume mounts, sessions
  and the offer set together. `BookingTransactionHandler` already provides the
  per-resource-type advisory lock that #169 requires, but the compute lock
  currently spans more than the compute provisional set.
* The admin namespace is already resource-typed. `/admin/pools/compute` was
  chosen so `/admin/pools/storage` can be added later without moving what
  exists.
* Pools and allocations are off-schema admin resources, like identities.
  Hibernate runs with `ddl-auto: create`, so new tables and renamed tables need
  no migration.

## Model and vocabulary

| Term | Meaning | Lifetime |
|---|---|---|
| Storage pool | A named, admin-managed source of storage capacity for one storage class, carrying its own acceptance policy. | Until an admin deletes it. |
| Storage class | The storage implementation a pool serves: volume-mounted or bind-mounted. | Declared on the pool and recorded on its offers and reservations. |
| Storage allocation | A size in GiB added to a pool for a window of time. Admin-managed. | Its window. |
| Storage reservation | Storage held for one session’s storage component: a size, a storage class and a reservation hull. | The hull (preparing, available, releasing). |
| Storage offer | A candidate storage reservation of one class returned by a pool and matched to a compute offer. | Until the set is confirmed or released. |
| Candidate set | One compute offer plus one storage offer for every storage resource in the request. | Until confirmation. |
| Policy | One of the pool’s own rules, asked through `accepts(criteria)`. | Lives with the pool. |
| Free space | Allocations minus reservations, per pool, per block. | Computed, never stored. |

The compute naming precedent is followed. The per-session storage component is a
reservation, so the internal entity tier becomes `AbstractStorageReservation`,
`SimpleStorageReservation` and their platform subclasses. The schema kind URI,
bean types, response fields and HTTP paths keep “storage resource”. Pools,
allocations and booking criteria keep “resource”, because they hold capacity or
describe a request rather than a booking.

The storage booking classes follow compute: `SimpleStorageReservationQuery`, its
query handler, `SimpleStorageOffer`, `SimpleStorageOfferBean`,
`SimpleStorageOfferFactory`, `SimpleStorageResourceCriteria`, and
`SimpleStorageResourcePool.makeOffers(criteria, queryHandler)`.

Every storage result carries a storage class. Explicit storage and storage for
`http(s)` data are volume-mounted; auto-created storage for `file://` data is
bind-mounted. The mock platform’s simple storage uses the volume class because
it is broker-managed rather than host-path-backed. The class travels from the
validator or linker result through the offer to the reservation, and a pool
serves exactly one class.

## Decisions

1. **Rename the storage entity tier; do not add a parallel type.** The
   reservation records the booked storage. The schema vocabulary, kind URIs,
   bean types, response fields and HTTP paths stay unchanged, as they did for
   compute.
2. **A storage allocation is an absolute size window.** A pool’s capacity in a
   block is the sum of the allocations covering it, in GiB. Overlapping
   allocations add up; an allocation with no end is open ended. There is no
   separate pool total and no global storage capacity setting.
3. **A storage reservation records requested and offered min/max size, and
   reserves the maximum offered size.** The block grid, hull rounding and live
   session phases are the same as compute. The reservation’s hull is the whole
   held window, not just its availability window.
4. **A storage reservation’s window is derived from the compute offer.** Storage
   preparation starts early enough for storage to be available before data
   preparation, the storage availability window covers the data download and
   compute execution, and the reservation ends after compute release. Stage 2
   fixes the epoch-anchored window as a prerequisite.
5. **Storage offer timing is fixed by the compute candidate, not searched.**
   The pipeline derives the exact storage interval each storage resource needs,
   and the storage query checks capacity over that interval. This keeps storage
   matching manageable and avoids a second free-run search per resource.
6. **Every storage result is booked.** Explicit storage, auto-created data
   storage and bind-mount/volume storage all get a storage reservation. A
   candidate set is complete only when every storage resource in the request has
   a storage offer. If any one cannot be booked, the candidate is dropped.
7. **Storage results and pools carry a storage class.** Explicit storage and
   storage for `http(s)` data are volume-mounted; auto-created storage for
   `file://` data is bind-mounted; the mock platform’s simple storage uses the
   volume class. The class travels from the validator/linker result through the
   offer to the reservation. A pool serves one class.
8. **Storage pool selection is first-fit by pool priority within a class.** For
   each storage resource, the pipeline asks enabled pools of that resource’s
   class in order and takes the first pool that accepts and has capacity.
   Compute pools keep their existing merge behaviour. A session may therefore
   hold storage reservations from different classes and pools.
9. **Two storage pools are seeded by default, one per class.**
   `default-volume` serves explicit storage and volume storage for `http(s)`
   data; `default-bind` serves bind-mount storage for `file://` data. Each gets
   an open-ended 1024 GiB fallback allocation when it declares none. Bind
   capacity is accounting-only in this iteration: bind reservations are booked
   uniformly, and a later bind-specific pool class can replace that capacity
   behaviour without changing the pipeline.
10. **The pipeline uses separate transactions per resource type.** The compute
    provisional set commits under the compute lock; the storage provisional set
    commits under the storage lock; confirmation runs in its own transaction and
    needs no capacity lock. No transaction spans compute and storage, and no lock
    is nested.
11. **Concrete first, generic later.** Storage pools, allocations and policies
    are implemented concretely. The generic abstraction (#216) is extracted
    only after the storage pool shape and the staged pipeline have been
    exercised.
12. **Storage pools remain off-schema admin resources.** No OpenAPI change. The
    one visible response addition is that a storage resource now carries its
    `size` from the schema, and its schedule reflects the real reservation
    window. Offers still do not name their pool.
13. **Validation stays split.** `stageOne` still decides whether the request is
    well formed and whether the platform can support the resource at all.
    Storage pool policy decides whether a specific pool wants it. A declining
    pool records its reason on the request context, and a request that no pool
    will serve returns `NO` with those messages.
14. **An allocation is never shrunk below live reservations.** Create, update
    and delete of a storage allocation run in the booking transaction under the
    storage lock, and a change that would leave any block over-committed is
    refused with `409 Conflict`.
15. **Deferred:** storage Prometheus metrics, the schema split (#217), a
    published pool SPI, a pool type registry, pool deletion, allocation
    calendars, force-shrinking allocations, bind-specific pool behaviour, data
    resource pools, and other capacity dimensions.

## Stages

| # | Stage | Size | Risk | Depends on | Behaviour change |
|---|---|---|---|---|---|
| 1 | Rename storage resources to storage reservations | L (mechanical) | Low | — | None |
| 2 | Storage reservations record size and hold the compute window | M | Medium (timing) | 1 | Storage size and schedule populated; no capacity check |
| 3 | Storage pools, storage classes and the admin API | M | Low | 1 | None |
| 4 | Storage allocations and capacity over time | M–L | Medium (SQL) | 3 | None until wired |
| 5 | Storage reservations join their pool and class | S–M | Low | 3, 4 | None; every reservation gets the pool for its class |
| 6 | The storage pool makes storage offers | M | Medium | 2, 5 | None on the request path; direct pool tests |
| 7 | Several storage pools, each with its own policy | M | Medium (diagnostics) | 6 | None on the request path; direct pool tests |
| 8 | The provisional compute-and-storage pipeline | L | High (transactions, matching) | 6, 7 | Yes: offers now book storage |
| 9 | Confirmation, release and concurrency hardening | M | Medium | 8 | No API change; safer provisional lifecycle |
| 10 | Generic resource pool abstraction (#216) | L (refactor) | Medium | 9 | None externally |
| 11 | Documentation, configuration examples and hardening | S–M | Low | 9, 10 | Configuration and documentation only |

Each stage leaves `main` working, ships on its own branch and pull request, and
gets its own implementation plan naming this document as its `Parent:`.

## Stage details

### Stage 1 — Rename storage resources to storage reservations

**Goal.** The per-session storage component is named for what it records.
No behaviour change.

**Changes.**
* Rename the storage entity tier: `AbstractStorageResource*` becomes
  `AbstractStorageReservation*`, `SimpleStorageResource*` becomes
  `SimpleStorageReservation*`, and the mock/docker storage entity tiers follow.
* Rename accessors and fields whose name is the type, including
  `getStorageResource`/`setStorageResource`, the session’s
  `getStorageResources`/`addStorageResource`, and the data and volume mount
  references that point at the entity.
* Rename the JPA tables to match: `abstractstoragereservations`,
  `simplestoragereservations`, `mocksimplestoragereservations`, and the docker
  bind/volume storage tables. Keep the schema kind URI, `WEBAPP_PATH`, bean
  types, response fields and HTTP paths.
* Update `AGENTS.md`, `docs/booking.md` references and any tests that name the
  internal types.

**Acceptance.**
* `./mvnw clean compile` and `./mvnw test` are green; only names changed.
* The `any` and `mock` Python suites pass unchanged, and `docker` if the
  platform is available. A validated request still returns the same session
  shape and storage kind.
* No OpenAPI, response or configuration change. A grep for the old internal
  class and table names finds none, apart from generated `Ivoa*` bean names.
* The rename is one commit; a half-renamed tree does not compile.

**Out of scope.** Storage pools, capacity, sizes and the public vocabulary.

**Risk.** Wide and mechanical, comparable to the compute rename (84 files and
about 500 occurrences). Use a scripted whole-identifier rename and let the
compiler find the misses.

### Stage 2 — Storage reservations record size and hold the compute window

**Goal.** A storage reservation persists the requested and offered size and
holds a real reservation window derived from the compute offer. This is the
foundation for a storage capacity query.

**Changes.**
* Add requested and offered min/max size columns to the simple storage
  reservation entity, in GiB, and populate them from the validated
  `SimpleStorageSize` and the storage offer.
* Default auto-created and bind-mount storage to 1 GiB when the request does not
  supply a size, so every storage reservation has a capacity figure.
* Derive the storage reservation’s preparing, available and releasing windows
  from the compute offer (and the data it serves) rather than from the
  uninitialised session timing fields. The reservation hull must cover the
  compute reservation hull.
* Populate the `size` and schedule fields in the storage response bean.
* Keep the storage lifecycle rules: storage is available before data
  preparation, data is available before compute preparation, and storage
  releases only after the dependent compute and data have released.

**Acceptance.**
* An explicit storage request persists its requested and offered sizes; an
  auto-created storage resource gets the default 1 GiB; the response carries
  the schema’s `size`.
* Stored storage windows are absolute instants, not epoch-based, and a state
  test asserts the storage reservation hull covers the compute reservation hull.
* The storage and data lifecycle still reaches `AVAILABLE` and releases in
  order; the `mock` and `docker` suites pass.
* No capacity enforcement and no pool reference yet.

**Out of scope.** Pools, allocations, policies and booking queries.

**Risk.** The window derivation changes the storage/data schedule that the
lifecycle processes act on. Keep the compute offer as the single source of
truth and test the storage/data/compute ordering explicitly.

### Stage 3 — Storage pools, storage classes and the admin API

**Goal.** A storage pool is a persisted, admin-managed entity that serves one
storage class. Booking is untouched.

**Changes.**
* `AbstractStorageResourcePool` and `SimpleStorageResourcePool`, with entities,
  tables, factories and a repository, following the compute pool pattern. A
  pool carries a name, description, priority, enabled flag and storage class.
* A `StorageClass` with `VOLUME` and `BIND`. The class is an admin and
  configuration field, not a schema field. The mock platform’s simple storage
  uses `VOLUME` because it is broker-managed rather than host-path-backed.
* Admin endpoints `POST` and `GET /admin/pools/storage`, plus
  `GET /admin/pools/storage/{uuid}`, guarded by the `ADMIN` authority, in the
  style of `AdminComputePoolsController`. Create and select responses carry the
  class; a create without a class defaults to `VOLUME`, and an unknown class is
  `400`.
* A startup bootstrap that always seeds `default-volume` and `default-bind`,
  and seeds any pools declared in the storage section of `pools.yaml`,
  idempotently by name.
* Extend the pools configuration binding to carry the storage list alongside
  the compute list, including each pool’s class; a deployment with no storage
  section still gets both defaults.

**Acceptance.**
* An admin can create and list storage pools; a non-admin gets `403`, and an
  unauthenticated request gets `401`.
* Both `default-volume` and `default-bind` exist after startup, with the correct
  class; running the bootstrap twice against the same database creates one pool
  per configured name.
* A pool response carries its UUID, name, description, priority, enabled flag
  and storage class; an invalid class is `400`.
* Offer generation and storage behaviour are unchanged.

**Out of scope.** Allocations, capacity, policies and pool-scoped offers.

### Stage 4 — Storage allocations and capacity over time

**Goal.** A storage pool’s supply is its allocations over time, and the
capacity query reads it from there. With the seeded default allocations,
nothing in the request path changes yet.

**Changes.**
* `AbstractStorageAllocation` and `SimpleStorageAllocation`, with entities, a
  table, factories and a repository. An allocation belongs to one pool, has a
  start, an optional end (open ended when absent), and a size in GiB. Sizes are
  positive and an end must be after its start.
* Admin endpoints `GET`/`POST /admin/pools/storage/{uuid}/allocations`, and
  `GET`/`POST`/`DELETE /admin/allocations/storage/{uuid}`. A create redirects
  with `303` to the allocation details.
* A storage allocation query that expands allocations over the same 60-second
  block grid as reservations, sums overlapping and open-ended allocations per
  block, and detects over-commitment against live storage reservations.
* An update or delete that would leave its pool with less capacity than its
  live reservations in any block is refused with `409 Conflict`, under the
  storage booking lock.
* The bootstrap gives every seeded storage pool the allocations declared for
  it, or one open-ended 1024 GiB allocation when it declares none. The bind
  pool’s allocation is accounting-only capacity, but the query treats both
  classes identically.

**Acceptance.**
* Allocation create, select, update and delete work; malformed sizes and
  windows are `400`, and over-committing changes are `409` and leave the row
  unchanged.
* Unit tests show overlapping allocations add up, an allocation that has not
  started contributes nothing before its start, and an ended allocation stops
  contributing after its end.
* Both default storage pools have an open-ended fallback allocation, including
  `default-bind`.
* The storage class does not alter the allocation query.
* No booking path reads storage allocations yet.

**Out of scope.** Wiring capacity into another query, policies, and the request
path.

**Risk.** The SQL is the substantial part. Reuse the compute block arithmetic
and the allocation-guard shape; keep the storage query readable and separate
until the generic stage.

### Stage 5 — Storage reservations join their pool and class

**Goal.** Every storage reservation records the pool and storage class it was
booked under. Still only the two default pools on the request path.

**Changes.**
* A non-null `pool` reference and a non-null `storageClass` on the abstract
  storage reservation entity, with the matching `getReservations()` view on the
  storage pool.
* The storage result bean and entity constructor carry the pool and class; the
  non-pipeline path stamps the default pool for the storage result’s class on
  every reservation it builds.
* The pool’s live-reservation view uses the same session phases as the booking
  query.

**Acceptance.**
* Every storage reservation, including one belonging to an `OFFERED` session,
  points to `default-volume` or `default-bind` matching its class; the foreign
  key is enforced and the class is stored.
* Explicit storage, `http(s)` storage and `file://` storage get `VOLUME`,
  `VOLUME` and `BIND` respectively.
* A pool’s `getReservations()` returns only that pool’s live storage
  reservations.
* Offer and session responses are unchanged; the Python suites pass.
* A state test asserts the stored storage pool and class for a session’s
  storage reservation.

**Out of scope.** Any change to how storage reservations are created or
counted.

### Stage 6 — The storage pool makes storage offers

**Goal.** `makeOffers(criteria, queryHandler)` on a storage pool; the storage
booking query checks capacity over the required storage interval. A pool only
offers for its own class. The request path still builds storage from validation
results, so behaviour is unchanged.

**Changes.**
* `SimpleStorageResourceCriteria`, carrying the request context, the storage
  validator result, the offer limit, the required storage interval, the
  requested size and the required storage class, with accessors for the
  owner/schedule needed by policies.
* `SimpleStorageOffer`, `SimpleStorageOfferBean`, `SimpleStorageOfferFactory`
  and `SimpleStorageReservationQueryHandler`, mirroring compute. An offer
  carries its pool and class.
* `SimpleStorageReservationQuery` scopes allocations and reservations to the
  pool, checks the required interval block by block, and returns at most the
  criteria’s offer limit. Offer sizes are the validated min/max; the query
  requires the maximum offered size to fit.
* `makeOffers` returns no offers when the criteria’s class does not match the
  pool, applies the pool’s policies later, scopes the query, and stamps every
  storage offer with its pool and class.
* Platform wiring exposes the storage offer factory and the storage query
  handler.

**Acceptance.**
* A unit test calls `makeOffers` directly on a volume pool and a bind pool and
  shows each pool’s offers depend only on its own allocations and reservations.
* A volume pool returns no offers for bind criteria, and a bind pool returns no
  offers for volume criteria.
* A pool returns no more than the limit in the criteria.
* An offer’s interval, size and class match the required storage resource.
* The request path and Python suites are unchanged.

**Out of scope.** Wiring storage offers into offer generation; policies;
multiple pools in the request path.

### Stage 7 — Several storage pools, each with its own policy

**Goal.** Enabled storage pools of each class form an ordered list, each pool
decides through its own policies whether a storage request is its business, and
the pipeline can ask them in order. Direct pool tests; the request path is still
unchanged.

**Changes.**
* Priority ordering and the enabled flag on the storage pool entity, seeded from
  `pools.yaml`, with an admin endpoint to change them. Each storage class has
  its own ordered list.
* A storage policy entity and the kinds `size-floor`, `size-ceiling`, `owner`
  and `time-window`. A pool with no policies accepts every request; all
  policies must accept. A declining pool records one message naming the policy
  and appends that policy’s reasons.
* Admin policy endpoints under `/admin/pools/storage/{uuid}/policies` and
  `/admin/policies/storage/{uuid}`, mirroring compute.
* Worked examples in `config/pools.yaml`: a generic volume pool, a bind pool, a
  small/large volume pair, and an owner/time-window pool.
* The bootstrap keeps seeding both default storage pools even when the
  configuration names others.

**Acceptance.**
* A large volume request is accepted by a large volume pool and declined by a
  small volume pool; a small request does the reverse.
* A bind request is considered only by bind pools, even when a volume pool has
  a policy that would otherwise accept it.
* A request between a floor and a ceiling pool is declined by both, and the
  reasons appear in the response messages.
* An invalid policy disables its pool rather than making it permissive.
* A test-double policy can route on owner or time without changing the pool
  body or the offer factory.
* The request path and Python suites are unchanged.

**Out of scope.** Asking several storage pools from the request path and the
cross-type pipeline.

**Risk.** Diagnostics and configuration validation. Keep one message per
declining pool, naming the policy, and reuse the compute policy result/message
shape until the generic stage.

### Stage 8 — The provisional compute-and-storage pipeline

**Goal.** The request path books compute and storage as a class-aware staged
pipeline: provisional compute offers, provisional storage reservations matched
to each candidate, confirmation of complete sets, and a final response. This is
the core of #169.

**Changes.**
* Split `OfferSetEntityFactoryImpl.create` and `stageTwo` into explicit
  pipeline steps. The offer set row exists before the provisional stages; the
  candidate sessions reference it.
* **Compute provisional step.** Run the compute offer factory and create the
  candidate sessions with their compute reservations under
  `BookingTransactionHandler.book(computeKind, ...)`, and commit them so a
  concurrent request sees them.
* **Storage provisional step.** Under
  `BookingTransactionHandler.book(storageKind, ...)`, for each candidate and
  each storage result:
  * derive the exact storage interval from the compute reservation hull,
    storage preparation and the data resources the storage serves;
  * ask enabled storage pools of the storage result’s class, in priority order,
    for an offer over that interval; if no enabled pool of that class exists,
    record a reason and mark the candidate rejected;
  * create the storage reservation from the offer, flush it before querying the
    next storage resource for the same candidate so the candidate cannot
    over-commit itself, and record the pool and class;
  * if any storage resource cannot be booked, mark the candidate rejected so
    its compute and storage reservations stop holding capacity.
* **Matching and ordering.** Reject candidates where storage cannot be
  available before data preparation, data cannot be available before compute
  starts, or storage cannot be held through compute release. Record a reason
  on the request context.
* **Confirmation.** In a separate transaction, sort the complete sets by
  compute offer start, cap at the effective request limit, reject unselected
  provisional sessions, set the offer set result and messages, and save the
  final offer set.
* Keep compute-only requests on the same path; a request with no storage
  results confirms its compute candidates exactly as today.
* Auto-created storage from the data linker and bind-mount/volume storage are
  booked like explicit storage. A `file://` result is offered only to bind
  pools; `http(s)` and explicit storage are offered only to volume pools.

**Acceptance.**
* A request with compute and storage returns offers whose storage reservations
  each carry a storage pool, a class, a size and a window ordered against the
  compute and data reservations.
* A request with both `file://` and `http(s)` data gets one bind reservation and
  one volume reservation, each attributed to the matching pool.
* Storage capacity is enforced per pool: a request whose maximum storage size
  does not fit the pool’s free blocks is dropped, and the response carries the
  reason. Bind capacity is accounting-only, but it is checked uniformly.
* A storage result with no enabled pool of its class is dropped with a message
  naming the class.
* A candidate that cannot book one of several storage resources is dropped,
  and no live storage reservation remains for it.
* Auto-created storage for a data resource is booked; bind-mount storage is
  booked under the same rule.
* Compute and storage provisional steps call the transaction handler
  separately; a unit test records two bookings with different resource types,
  and no transaction spans both.
* Concurrent storage requests cannot over-commit; the existing compute race
  test still passes.
* Compute-only offer counts, start times and sizes are unchanged; the `mock`,
  `docker` and `any` suites pass with the default configuration.

**Out of scope.** Accept-time re-validation, historical cleanup, the generic
abstraction, and storage metrics.

**Risk.** High. This is the transaction and lifecycle centre of the plan. Keep
the lock order compute then storage, never nested; flush reservations within
the storage transaction; make every candidate expire or reject on failure; and
test the transaction boundaries directly.

### Stage 9 — Confirmation, release and concurrency hardening

**Goal.** The provisional-to-confirmed lifecycle is safe under concurrency,
failure and abandonment. No API shape change.

**Changes.**
* Ensure every provisional candidate gets an expiry processing request, so a
  pipeline that fails between stages leaves no reservation held beyond its
  expiry.
* Make confirmation reject unselected candidates and release their compute and
  storage reservations; make accepting one offer reject its siblings and
  release both reservation families.
* Add diagnostics around the stage boundaries and the lock identity, so a
  regression that nests the locks or holds one across stages is visible.
* Optionally re-validate capacity at accept as a safety net, without changing
  the accept path’s result for a normal offer.
* Extend the state tests: storage over-commit race, mixed compute/storage race,
  mixed bind/volume race, allocation update/delete against live reservations,
  and abandoned provisional candidates.

**Acceptance.**
* Concurrent compute-only, storage-only and mixed requests, including requests
  with both storage classes, respect both invariants; no over-commit and no
  deadlock.
* A failed or abandoned pipeline leaves no live reservations after expiry or
  rejection.
* Accepting one offer releases the compute and storage reservations of its
  siblings.
* Allocation writes racing a booking cannot commit against a stale
  reservation view.
* The response shape and the Python suites are unchanged.

**Out of scope.** Generic extraction, storage metrics, schema split.

### Stage 10 — Generic resource pool abstraction (#216)

**Goal.** Compute and storage pools share one abstraction after both have
exercised the same shape, with no external change.

**Changes.**
* Extract the shared pool root and entity base: name, description, priority,
  enabled flag, live reservations and the pool’s relation to its allocations
  and policies.
* Extract a shared allocation base (pool, start, end) while keeping the
  dimension columns (`cores`/`memory` vs `size`) in the concrete types.
* Extract the shared block/hull arithmetic and capacity-guard shape; keep the
  compute and storage SQL distinct.
* Generalise the policy result, messages and criteria view so a policy is
  independent of the resource type; keep the concrete policy kinds.
* Share the admin request/response records and the common validation, while
  preserving `/admin/pools/compute` and `/admin/pools/storage`.
* Keep the storage class on the storage-specific pool interface, not the
  generic pool base. Class filtering stays in the storage offer factory and the
  pipeline.
* Preserve `makeOffers` as the pool seam so a later bind-specific pool subclass
  or strategy can override offer/capacity behaviour without changing the
  pipeline.
* Refactor the compute and storage implementations onto the shared pieces.

**Acceptance.**
* All Java unit tests and the Python suites pass.
* The admin paths, response fields, configuration keys and pool kind URIs are
  unchanged.
* Compute and storage equivalence tests show the same offers and capacity
  results as before the refactor.
* The four questions in #216 are answered in the stage plan: pool root shape,
  common versus type-specific admin surface, shared capacity query versus
  shared arithmetic, and resource-type-independent policy.
* A test-double bind pool with a different `makeOffers` behaves differently
  without any pipeline change.
* No pool SPI is published.

**Out of scope.** A published SPI, a pool type registry, schema types and
bind-specific pool behaviour.

**Risk.** A broad refactor over working code. Land it as one commit with
equivalence tests, and do not change behaviour or names beyond the shared
base.

### Stage 11 — Documentation, configuration examples and hardening

**Goal.** The storage pool and cross-type booking model is written down and the
configuration template shows how to use it.

**Changes.**
* Update `docs/booking.md`: storage reservations, storage classes, the two
  default pools, storage allocations, the staged pipeline, ordering, messages,
  the block grid and the invariant. Explain that bind capacity is accounting-only
  until a bind-specific pool class replaces it.
* Update `AGENTS.md`: the data model, the storage admin endpoints, the
  package layout and the configuration keys.
* Update `config/pools.yaml` with the storage section and worked examples:
  the default volume and bind pools with their fallback allocations, a generic
  volume pool, a bind pool, size floor/ceiling pools and an owner/time-window
  pool.
* Update the issue references and close or annotate #169, #78 and #216 as the
  stages land.
* Add the licence header, copyright year and AIMetrics to every new file and
  commit.

**Acceptance.**
* A fresh deployment with no storage configuration serves explicit and
  `http(s)` storage through `default-volume`, and `file://` storage through
  `default-bind`, each with its fallback allocation.
* A deployment with configured storage pools serves them from those pools and
  their policies, respecting each pool’s class.
* `docs/booking.md` explains how to add a storage pool, allocation and policy,
  how the storage classes are selected, and what the storage invariant means.
* All Java tests and the `any`, `mock` and `docker` suites pass.

**Out of scope.** Schema split (#217), storage Prometheus metrics, pool SPI,
pool deletion, allocation calendars.

## Cross-cutting design

### Data flow

1. `stageOne` validates the request and registers storage results, including
   auto-created storage for data resources and the storage class each result
   needs. It remains resource-type neutral.
2. The compute provisional step runs the compute pool queries and commits the
   candidate sessions and compute reservations.
3. The storage provisional step derives each storage resource’s required
   interval from the candidate compute reservation and its data resources,
   asks enabled pools of the result’s class first-fit, and commits the storage
   reservations.
4. Confirmation selects complete candidate sets, rejects the rest, and builds
   the final response from the confirmed sessions.
5. A confirmed storage reservation carries its pool, class, size and hull; a
   rejected or expired session releases both its compute and storage
   reservations.

### Transactions and locking

* The compute provisional set is the atomic unit for compute.
* The storage provisional set is the atomic unit for storage, including any
  admin allocation write it races with.
* Both storage classes share the storage booking lock. Class selection happens
  before the pool query and does not introduce a per-class lock.
* Locks are keyed on the resource type kind, as they are today. Compute is
  locked before storage and never the other way round; no lock spans both
  stages.
* Confirmation changes phases only and runs without a capacity lock.
* Within the storage transaction, storage reservations are flushed before the
  next storage query so a candidate cannot over-commit itself.

### Public API, schema and configuration

* No OpenAPI schema change. Storage pools, allocations and policies are
  off-schema admin resources, like compute pools.
* The storage class is not an OpenAPI field. It is inferred by the storage
  validator or data linker and recorded internally; admin pool create and
  select responses expose it.
* New admin paths:
  * `/admin/pools/storage`
  * `/admin/pools/storage/{uuid}/allocations`
  * `/admin/allocations/storage/{uuid}`
  * `/admin/pools/storage/{uuid}/policies`
  * `/admin/policies/storage/{uuid}`
* Existing offer and session responses keep their shape. Storage resources now
  carry the schema’s `size`, and their schedule reflects the reservation
  window.
* Configuration gains `calycopis.broker.pools.storage` in `pools.yaml`, with a
  `class` for each pool; a deployment with no storage section keeps the two
  seeded default pools and their fallback allocations.
* New tables: storage pool, allocation and policy tables, with the storage
  class on the pool and reservation tables; the storage reservation tables are
  renamed in Stage 1. Hibernate rebuilds the schema on every start, so there is
  no migration.

### Failure modes and edge cases

* No enabled storage pool for a class: `NO` with a message analogous to
  `urn:no-enabled-compute-pools`, naming the class.
* A storage policy declines: one message per pool naming the policy, followed
  by its reasons.
* No capacity for a required storage interval: the candidate is dropped; if
  all candidates are dropped the response is `NO`.
* A request with several storage resources, possibly of different classes: all
  must be booked, possibly from different pools, or the candidate is dropped.
* Auto-created volume storage, bind-mount storage and explicit storage are
  booked uniformly, with the 1 GiB default size when no size is supplied.
* A storage request with no size: defaulted by the validator; a reservation
  always has a size.
* Allocation update/delete that would over-commit: `409 Conflict`, under the
  storage lock shared by both classes.
* Provisional failure or abandonment: candidate rejected or expired, so no
  reservation remains live.
* Concurrent mixed requests: compute and storage locks are separate; no
  nested locks, no deadlock.
* Backward compatibility: no storage configuration still serves the previous
  requests through `default-volume` and `default-bind`.

### Testing and acceptance

* **Java unit tests:** storage reservation size/window and class; storage
  allocation query (overlap, open ends, start/end); allocation guard; storage
  pool offers on volume and bind pools; storage policies; bootstrap idempotency
  for both defaults; generic equivalence.
* **Python suites:** `any`, `mock`, `docker` for the API and lifecycle;
  `states` for reservation windows, pool and class attribution, capacity
  invariants, mixed bind/volume requests and the compute/storage races. State
  tests use direct database access and may be excluded from CI like the existing
  race tests.
* **Equivalence checks:** compute-only offers unchanged at every stage; storage
  offers unchanged with the two default pools and their fallback allocations.
* **Regression checks:** each stage compiles, passes the Java tests, and passes
  the Python suites relevant to its platform.
* **Commands:** `./mvnw clean compile`, `./mvnw test`, and
  `tests/python/bin/run-tests.sh` (or the per-suite `pytest` invocations).

## Deferred

* **Storage Prometheus metrics and Grafana panels.** The compute pool gauges
  remain the only pool metrics until a separate piece of work asks for storage.
* **OpenAPI schema extension for pools, allocations and policies (#217).**
  Offers still do not name their pool.
* **A published pool SPI and a pool type registry.** The interfaces are
  reviewed as API in Stage 10 but not published.
* **Pool deletion.** Allowed only when it has no live reservations, and then
  with its allocations.
* **Allocation calendars**, force-shrinking an allocation, and other capacity
  dimensions.
* **A bind-specific pool class and data resource pools.** Bind pools use the
  uniform accounting model for now; a later class or strategy can change their
  offer and capacity behaviour. Booking data resources from pools is a future
  resource type; this plan enforces data/storage timing but does not pool data
  capacity.
* **Accept-time re-validation as a hard requirement.** It is a safety net, not
  the mechanism that closes the booking race.

## Questions to settle in the detailed plans

These are implementation choices, not changes to the stage boundaries or
decisions above.

1. **Exact storage window offsets.** How far storage preparation precedes the
   compute preparation, and whether the storage available window starts at the
   compute prepare start or earlier. The requirement is fixed: storage
   available before data preparation, data available before compute start,
   storage held through compute release.
2. **Policy floor/ceiling dimensions.** Recommend floor on requested minimum
   size and ceiling on requested maximum size, matching what a storage pool
   owner would expect.
3. **Bind-specific pool evolution.** Recommend a later bind pool subclass or
   strategy that overrides `makeOffers` and keeps the pipeline contract, rather
   than adding bind-specific branches to the pipeline now.
4. **Generic abstraction shape.** Recommend a shared pool root and entity base,
   a shared allocation base, shared block arithmetic, and concrete
   offers/criteria per resource type, rather than a fully parameterised
   `AbstractResourcePool<A, R>` if the two dimensions do not agree.
5. **Storage query parameterisation.** Recommend a concrete storage query until
   Stage 10, then extract only the shared arithmetic.

## Delivery and first steps

1. Record this high-level plan as
   `agents/plans/20261125-01-storage-resource-pools-and-booking-across-resource-types-059e5e37.plan.md`,
   with the UUID comment, the stage todos as `pending`, the `## Status` section
   and the `## AIMetrics` block.
2. Write the Stage 1 implementation plan with this document as its `Parent:`.
3. Implement Stage 1 on its own branch and pull request.
4. Repeat for each stage, updating this plan’s Status and AIMetrics as the
   stages land.

## Status

status: in-progress
details: High-level plan written on 2026-11-25 and updated on 2026-11-26 to add
  storage classes, two default storage pools (`default-volume` and
  `default-bind`) and the bind capacity decision. Stage 1, the storage-resource
  rename to storage reservations, is merged on `main` in PR #234 at `3224d127`.
  Stage 2, recording the reservation size and holding the compute window, is
  merged on `main` in [PR #235](https://github.com/uksrc/Calycopis-broker/pull/235)
  at `aacee00d`. Stage 3, storage pools, storage classes and the admin API, is
  implemented on branch `20261128-zrq-storage-pools-and-admin-api`, commit
  `a3181e2b`, and open as
  [PR #236](https://github.com/uksrc/Calycopis-broker/pull/236); its plan is
  [20261128-01-storage-pools-storage-classes-and-the-admin-api](20261128-01-storage-pools-storage-classes-and-the-admin-api-48dcd834.plan.md).
  Merge is pending. Stages 4–11 remain planned. The compute pool
  prerequisite is complete and merged on `main`. Issues:
  [#169](https://github.com/uksrc/Calycopis-broker/issues/169),
  [#78](https://github.com/uksrc/Calycopis-broker/issues/78) and
  [#216](https://github.com/uksrc/Calycopis-broker/issues/216).

## AIMetrics

```text
AIMetrics: [
    {
    "timestamp": "2026-11-25T06:41:13",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 100,
      "units": "%"
      }
    },
    {
    "timestamp": "2026-11-26T20:29:58",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-v4.1-flash-1017",
    "contribution": {
      "value": 100,
      "units": "%"
      }
    },
    {
    "interval": "2026-11-28T02:24:00/2026-11-28T02:24:35",
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
    },
    {
    "interval": "2026-11-28T03:31:00/2026-11-28T03:40:00",
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
