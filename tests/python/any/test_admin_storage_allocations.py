#
# <meta:header>
#   <meta:licence>
#     Copyright (C) 2026 University of Manchester.
#
#     This information is free software: you can redistribute it and/or modify
#     it under the terms of the GNU General Public License as published by
#     the Free Software Foundation, either version 3 of the License, or
#     (at your option) any later version.
#
#     This information is distributed in the hope that it will be useful,
#     but WITHOUT ANY WARRANTY; without even the implied warranty of
#     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#     GNU General Public License for more details.
#
#     You should have received a copy of the GNU General Public License
#     along with this program.  If not, see <http://www.gnu.org/licenses/>.
#   </meta:licence>
# </meta:header>
#
# AIMetrics: [
#     {
#     "interval": "2026-11-28T04:50:00/2026-11-28T05:40:00",
#     "name": "@deepseek-ai/dsh",
#     "version": "0.2.0-rc.2",
#     "model": "deepseek-v4.1-flash-1017",
#     "contribution": {
#       "value": 100,
#       "units": "%"
#       }
#     }
#   ]
#
#

"""
Integration tests for the admin storage allocation endpoints.

Allocations are off-schema admin resources, so these tests use urllib directly.
They cover the collection under a storage pool, the item by UUID, the authority
rules, the values the controller refuses, the startup fallback, and the guard
that keeps a pool from being left with less capacity than its live storage
reservations need.

Requires:
  - A running Calycopis broker.
  - The admin credentials from admin.yaml, which conftest reads.
  - The test identities seeded by conftest.py.
"""

import base64
import json
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone

import pytest

from calycopis_conftest import admin_credentials
from calycopis_openapi_client.models import (
    ComponentMetadata,
    DockerImageSpec,
    ExecutionRequest,
)
from calycopis_openapi_client.models.simple_execution_session_phase import (
    SimpleExecutionSessionPhase,
)
from calycopis_openapi_client.wrappers import (
    DockerContainer,
    SimpleDataResource,
)

POOLS_PATH = "/admin/pools/storage"
ALLOCATIONS_PATH = "/admin/allocations/storage"

CANTLIEI_IMAGE = "ghcr.io/zarquan/heliophorus-cantliei:sha-831ee57"
CANTLIEI_DIGEST = "sha256:6e495692cc6f1cae2023f261f433d4691aa70b19416730f8301e45fbb74bc526"

BROKER_URL = None


@pytest.fixture(autouse=True)
def _use_broker_url(broker_url):
    """Make the broker URL available to the raw HTTP helpers."""
    global BROKER_URL
    BROKER_URL = broker_url


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

class _NoRedirect(urllib.request.HTTPRedirectHandler):
    """Keep a 303 visible instead of following it to the new resource."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


_OPENER = urllib.request.build_opener(_NoRedirect)


def _auth_header(username: str, password: str) -> dict:
    """Build a basic-auth header for the given credentials."""
    credentials = base64.b64encode(
        f"{username}:{password}".encode("utf-8")
    ).decode("utf-8")
    return {"Authorization": f"Basic {credentials}"}


def _admin_auth() -> dict:
    """The basic-auth header for the broker admin."""
    username, password = admin_credentials()
    return _auth_header(username, password)


def _call(method: str, path: str, body=None, headers=None):
    """Make a request and return (status, headers, body text).

    HTTP errors are returned rather than raised, because the status codes are
    what these tests assert. Redirects are not followed.
    """
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    request = urllib.request.Request(
        BROKER_URL + path,
        data=data,
        method=method
    )
    request.add_header("Accept", "application/json")
    if data is not None:
        request.add_header("Content-Type", "application/json")
    for name, value in (headers or {}).items():
        request.add_header(name, value)
    try:
        with _OPENER.open(request, timeout=10) as response:
            return response.status, dict(response.headers), response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        return error.code, dict(error.headers), error.read().decode("utf-8")


def _pool_name() -> str:
    """A storage pool name that no other test run has used."""
    return "storage-alloc-pool-" + uuid.uuid4().hex[:12]


def _create_pool(name: str, storage_class=None):
    """Create a storage pool and return its identifier and body."""
    body = {
        "name": name,
        "description": "A pool for storage allocation tests.",
    }
    if storage_class is not None:
        body["storageClass"] = storage_class
    status, _, response_body = _call(
        "POST",
        POOLS_PATH,
        body=body,
        headers=_admin_auth()
    )
    assert status == 201, f"Expected 201 Created, got {status}: {response_body}"
    created = json.loads(response_body)
    return created["uuid"], created


def _create_allocation(pool_uuid: str, body):
    """Create an allocation and return (status, headers, body)."""
    return _call(
        "POST",
        f"{POOLS_PATH}/{pool_uuid}/allocations",
        body=body,
        headers=_admin_auth()
    )


def _list_allocations(pool_uuid: str):
    """Return the allocations of one pool."""
    status, _, body = _call(
        "GET",
        f"{POOLS_PATH}/{pool_uuid}/allocations",
        headers=_admin_auth()
    )
    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    return json.loads(body)


def _get_allocation(allocation_uuid: str):
    """Return (status, body) for one allocation."""
    status, _, body = _call(
        "GET",
        f"{ALLOCATIONS_PATH}/{allocation_uuid}",
        headers=_admin_auth()
    )
    return status, body


def _update_allocation(allocation_uuid: str, body):
    """Update one allocation and return (status, body)."""
    status, _, response_body = _call(
        "POST",
        f"{ALLOCATIONS_PATH}/{allocation_uuid}",
        body=body,
        headers=_admin_auth()
    )
    return status, response_body


def _delete_allocation(allocation_uuid: str):
    """Delete one allocation and return (status, body)."""
    status, _, body = _call(
        "DELETE",
        f"{ALLOCATIONS_PATH}/{allocation_uuid}",
        headers=_admin_auth()
    )
    return status, body


def _isoformat(value: datetime) -> str:
    """Format an aware datetime as a UTC ISO-8601 instant."""
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def _create_current_allocation(pool_uuid: str, size: int):
    """Create an open-ended allocation that is already available."""
    status, headers, body = _create_allocation(
        pool_uuid,
        {"size": size, "start": "1970-01-01T00:00:00Z"}
    )
    assert status == 303, f"Expected 303 See Other, got {status}: {body}"
    allocation_uuid = headers.get("Location", "").rsplit("/", 1)[-1]
    assert allocation_uuid, f"No allocation UUID in Location [{headers}]"
    return allocation_uuid


def _request_for_data(name: str, count: int) -> ExecutionRequest:
    """A request whose auto-created storage reserves count GiB."""
    return ExecutionRequest(
        executable=DockerContainer(
            meta=ComponentMetadata(name=f"{name}-exec"),
            image=DockerImageSpec(
                locations=[CANTLIEI_IMAGE],
                digest=CANTLIEI_DIGEST,
            ),
            command=["5", "0"],
        ),
        data=[
            SimpleDataResource(
                meta=ComponentMetadata(name=f"{name}-data-{index}"),
                location=f"https://example.org/data/{name}-{index}.fits",
            )
            for index in range(count)
        ],
    )


def _submit(client, request: ExecutionRequest):
    """Submit a request and return the OfferSetResponse."""
    return client.submit_execution(request, follow_redirect=True)


def _reject_offers(client, response):
    """Reject every offer in a YES response."""
    for offer in response.offers:
        client.set_session_phase(
            offer.meta.uuid,
            SimpleExecutionSessionPhase.REJECTED,
        )
        client.wait_for_phase(
            offer.meta.uuid,
            {
                SimpleExecutionSessionPhase.REJECTED,
                SimpleExecutionSessionPhase.CANCELLED,
                SimpleExecutionSessionPhase.FAILED,
                SimpleExecutionSessionPhase.EXPIRED,
            },
            timeout=30.0,
            interval=1.0,
        )


def _wait_for_delete(client, allocation_uuid):
    """Delete an allocation, allowing a moment for released storage to settle."""
    deadline = time.monotonic() + 10.0
    status, body = _delete_allocation(allocation_uuid)
    while status == 409 and time.monotonic() < deadline:
        time.sleep(0.5)
        status, body = _delete_allocation(allocation_uuid)
    return status, body


# ---------------------------------------------------------------------------
# Startup fallback
# ---------------------------------------------------------------------------

def test_default_storage_pools_get_a_fallback_allocation():
    """Both seeded storage pools carry the open-ended 1024 GiB fallback."""
    status, _, body = _call("GET", POOLS_PATH, headers=_admin_auth())
    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    pools = {pool["name"]: pool for pool in json.loads(body)}
    for name in ("default-volume", "default-bind"):
        assert name in pools, f"The seeded pool [{name}] is missing from {pools}"
        allocations = _list_allocations(pools[name]["uuid"])
        seeded = [
            allocation
            for allocation in allocations
            if allocation["size"] == 1024
            and allocation["start"] == "1970-01-01T00:00:00Z"
            and allocation["end"] is None
        ]
        assert len(seeded) == 1, (
            f"The seeded pool [{name}] should hold exactly one fallback "
            f"allocation, got {allocations}"
        )


# ---------------------------------------------------------------------------
# Allocation CRUD
# ---------------------------------------------------------------------------

def test_create_allocation_redirects_to_its_details():
    """Creating an allocation answers 303 with the item's Location."""
    pool_uuid, _ = _create_pool(_pool_name())
    status, headers, body = _create_allocation(
        pool_uuid,
        {"size": 8},
    )

    assert status == 303, f"Expected 303 See Other, got {status}: {body}"
    location = headers.get("Location", "")
    prefix = f"{ALLOCATIONS_PATH}/"
    assert location.startswith(prefix), (
        f"Expected Location to start with [{prefix}], got [{location}]"
    )
    allocation_uuid = location[len(prefix):]
    status, body = _get_allocation(allocation_uuid)
    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    allocation = json.loads(body)
    assert allocation["pool"] == pool_uuid
    assert allocation["size"] == 8
    assert allocation["end"] is None


def test_create_allocation_carries_its_window():
    """A created allocation keeps the declared start and end."""
    pool_uuid, _ = _create_pool(_pool_name())
    start = datetime.now(timezone.utc).replace(microsecond=0)
    end = start + timedelta(hours=2)
    status, headers, body = _create_allocation(
        pool_uuid,
        {"size": 4, "start": _isoformat(start), "end": _isoformat(end)},
    )

    assert status == 303, f"Expected 303 See Other, got {status}: {body}"
    allocation_uuid = headers.get("Location", "").rsplit("/", 1)[-1]
    status, body = _get_allocation(allocation_uuid)
    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    allocation = json.loads(body)
    assert allocation["size"] == 4
    assert allocation["start"] == _isoformat(start)
    assert allocation["end"] == _isoformat(end)


def test_list_allocation_collection_returns_created_items():
    """The pool's collection lists every allocation the pool holds."""
    pool_uuid, _ = _create_pool(_pool_name())
    first = _create_current_allocation(pool_uuid, 1)
    second = _create_current_allocation(pool_uuid, 2)

    allocations = _list_allocations(pool_uuid)
    identifiers = {allocation["uuid"] for allocation in allocations}
    assert identifiers == {first, second}


def test_update_allocation_changes_size_and_window():
    """An update replaces the values returned by the item endpoint."""
    pool_uuid, _ = _create_pool(_pool_name())
    allocation_uuid = _create_current_allocation(pool_uuid, 1)
    end = datetime.now(timezone.utc).replace(microsecond=0) + timedelta(hours=1)

    status, body = _update_allocation(
        allocation_uuid,
        {"size": 3, "start": "1970-01-01T00:00:00Z", "end": _isoformat(end)},
    )
    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    updated = json.loads(body)
    assert updated["size"] == 3
    assert updated["end"] == _isoformat(end)

    status, body = _get_allocation(allocation_uuid)
    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    assert json.loads(body)["size"] == 3


def test_delete_allocation_removes_it():
    """Deleting an allocation answers 204 and the item becomes 404."""
    pool_uuid, _ = _create_pool(_pool_name())
    allocation_uuid = _create_current_allocation(pool_uuid, 1)

    status, body = _delete_allocation(allocation_uuid)
    assert status == 204, f"Expected 204 No Content, got {status}: {body}"
    status, _ = _get_allocation(allocation_uuid)
    assert status == 404, f"Expected 404 Not Found after delete, got {status}"


def test_bind_pool_supports_allocation_crud():
    """The same allocation flow works on a BIND pool as on a VOLUME pool."""
    pool_uuid, pool = _create_pool(_pool_name(), "BIND")
    assert pool["storageClass"] == "BIND"
    allocation_uuid = _create_current_allocation(pool_uuid, 1)

    status, body = _get_allocation(allocation_uuid)
    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    assert json.loads(body)["size"] == 1

    end = datetime.now(timezone.utc).replace(microsecond=0) + timedelta(hours=1)
    status, body = _update_allocation(
        allocation_uuid,
        {"size": 2, "start": "1970-01-01T00:00:00Z", "end": _isoformat(end)},
    )
    assert status == 200, f"Expected 200 OK, got {status}: {body}"
    assert json.loads(body)["size"] == 2

    status, body = _delete_allocation(allocation_uuid)
    assert status == 204, f"Expected 204 No Content, got {status}: {body}"
    status, _ = _get_allocation(allocation_uuid)
    assert status == 404, f"Expected 404 Not Found after delete, got {status}"


def test_unknown_pool_and_allocation_are_not_found():
    """An unknown pool or allocation answers 404."""
    missing = uuid.uuid4()
    status, headers, body = _create_allocation(missing, {"size": 1})
    assert status == 404, f"Expected 404 Not Found, got {status}: {body}"
    status, body = _get_allocation(str(missing))
    assert status == 404, f"Expected 404 Not Found, got {status}: {body}"


def test_malformed_identifiers_are_bad_requests():
    """A UUID that cannot be parsed answers 400."""
    status, _, body = _call(
        "GET",
        f"{POOLS_PATH}/not-a-uuid/allocations",
        headers=_admin_auth()
    )
    assert status == 400, f"Expected 400 Bad Request, got {status}: {body}"
    status, _, body = _call(
        "GET",
        f"{ALLOCATIONS_PATH}/not-a-uuid",
        headers=_admin_auth()
    )
    assert status == 400, f"Expected 400 Bad Request, got {status}: {body}"


@pytest.mark.parametrize(
    "body",
    [
        {},
        {"size": 0},
        {"size": -1},
        {"size": 1, "end": "1970-01-01T00:00:00Z"},
        {"size": 1, "start": "2026-12-02T00:00:00Z", "end": "2026-12-01T00:00:00Z"},
        {"size": 1, "start": "not-an-instant"},
    ],
)
def test_create_refuses_invalid_allocation_values(body):
    """A creation with no usable size or window answers 400."""
    pool_uuid, _ = _create_pool(_pool_name())
    status, _, response_body = _create_allocation(pool_uuid, body)
    assert status == 400, (
        f"Expected 400 Bad Request for {body}, got {status}: {response_body}"
    )


# ---------------------------------------------------------------------------
# Authority
# ---------------------------------------------------------------------------

def test_admin_allocation_endpoints_require_authentication(alice_creds):
    """Every allocation verb is 401 without credentials and 403 for a user."""
    path = f"{ALLOCATIONS_PATH}/{uuid.uuid4()}"
    alice = _auth_header(alice_creds["username"], alice_creds["password"])
    for method, body in (
        ("GET", None),
        ("POST", {"size": 1}),
        ("DELETE", None),
    ):
        status, _, response_body = _call(method, path, body=body)
        assert status == 401, (
            f"Expected 401 Unauthorized for {method}, "
            f"got {status}: {response_body}"
        )
        status, _, response_body = _call(method, path, body=body, headers=alice)
        assert status == 403, (
            f"Expected 403 Forbidden for {method}, got {status}: {response_body}"
        )


# ---------------------------------------------------------------------------
# Over-commit guard
# ---------------------------------------------------------------------------

def test_delete_allocation_with_live_storage_is_refused(client, clean_broker):
    """A delete that would leave live storage without capacity is refused."""
    pool_uuid, _ = _create_pool(_pool_name())
    allocation_uuid = _create_current_allocation(pool_uuid, 1)
    response = _submit(client, _request_for_data("delete-guard", 1))
    assert response.result == "YES", (
        f"Expected YES, got {response.result}. "
        f"Messages: {response.meta.messages if response.meta else 'none'}"
    )

    status, body = _delete_allocation(allocation_uuid)
    assert status == 409, f"Expected 409 Conflict, got {status}: {body}"
    status, body = _get_allocation(allocation_uuid)
    assert status == 200, f"The refused delete must leave the row, got {status}"
    assert json.loads(body)["size"] == 1

    _reject_offers(client, response)
    status, body = _wait_for_delete(client, allocation_uuid)
    assert status == 204, (
        f"Expected 204 No Content after the reservation was released, "
        f"got {status}: {body}"
    )


def test_delete_refused_when_overlapping_allocations_fall_below_demand(
    client, clean_broker
):
    """Two allocations may cover a demand that one of them cannot."""
    pool_uuid, _ = _create_pool(_pool_name())
    first = _create_current_allocation(pool_uuid, 1)
    _create_current_allocation(pool_uuid, 1)
    response = _submit(client, _request_for_data("overlap-guard", 2))
    assert response.result == "YES", (
        f"Expected YES, got {response.result}. "
        f"Messages: {response.meta.messages if response.meta else 'none'}"
    )

    status, body = _delete_allocation(first)
    assert status == 409, f"Expected 409 Conflict, got {status}: {body}"
    status, body = _get_allocation(first)
    assert status == 200, f"The refused delete must leave the row, got {status}"
    _reject_offers(client, response)


def test_shrinking_an_allocation_below_live_demand_is_refused(client, clean_broker):
    """An update that cannot cover live storage is refused and leaves the row."""
    pool_uuid, _ = _create_pool(_pool_name())
    allocation_uuid = _create_current_allocation(pool_uuid, 2)
    response = _submit(client, _request_for_data("shrink-guard", 2))
    assert response.result == "YES", (
        f"Expected YES, got {response.result}. "
        f"Messages: {response.meta.messages if response.meta else 'none'}"
    )

    status, body = _update_allocation(
        allocation_uuid,
        {"size": 1, "start": "1970-01-01T00:00:00Z"},
    )
    assert status == 409, f"Expected 409 Conflict, got {status}: {body}"
    status, body = _get_allocation(allocation_uuid)
    assert status == 200, f"The refused update must leave the row, got {status}"
    assert json.loads(body)["size"] == 2
    _reject_offers(client, response)


def test_future_allocation_does_not_cover_current_demand(client, clean_broker):
    """A future allocation is not supply for a reservation in the current block."""
    pool_uuid, _ = _create_pool(_pool_name())
    current = _create_current_allocation(pool_uuid, 2)
    future_start = datetime.now(timezone.utc).replace(microsecond=0) + timedelta(hours=1)
    status, headers, body = _create_allocation(
        pool_uuid,
        {"size": 2, "start": _isoformat(future_start)},
    )
    assert status == 303, f"Expected 303 See Other, got {status}: {body}"
    response = _submit(client, _request_for_data("future-guard", 2))
    assert response.result == "YES", (
        f"Expected YES, got {response.result}. "
        f"Messages: {response.meta.messages if response.meta else 'none'}"
    )

    status, body = _delete_allocation(current)
    assert status == 409, f"Expected 409 Conflict, got {status}: {body}"
    status, body = _get_allocation(current)
    assert status == 200, f"The refused delete must leave the row, got {status}"
    _reject_offers(client, response)


def test_ended_allocation_does_not_cover_current_demand(client, clean_broker):
    """An allocation that has ended is not supply for a current reservation."""
    pool_uuid, _ = _create_pool(_pool_name())
    current = _create_current_allocation(pool_uuid, 2)
    now = datetime.now(timezone.utc).replace(microsecond=0)
    status, headers, body = _create_allocation(
        pool_uuid,
        {
            "size": 2,
            "start": "1970-01-01T00:00:00Z",
            "end": _isoformat(now - timedelta(minutes=5)),
        },
    )
    assert status == 303, f"Expected 303 See Other, got {status}: {body}"
    response = _submit(client, _request_for_data("ended-guard", 2))
    assert response.result == "YES", (
        f"Expected YES, got {response.result}. "
        f"Messages: {response.meta.messages if response.meta else 'none'}"
    )

    status, body = _delete_allocation(current)
    assert status == 409, f"Expected 409 Conflict, got {status}: {body}"
    status, body = _get_allocation(current)
    assert status == 200, f"The refused delete must leave the row, got {status}"
    _reject_offers(client, response)
