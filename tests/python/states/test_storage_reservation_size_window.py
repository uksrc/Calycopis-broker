#
# <meta:header>
#   <meta:licence>
#     Copyright (c) 2026, University of Manchester (http://www.manchester.ac.uk/)
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
#     along with this software. If not, see <http://www.gnu.org/licenses/>.
#   </meta:licence>
# </meta:header>
#
# AIMetrics: [
#     {
#     "interval": "2026-11-27T18:40:34/2026-11-27T18:47:00",
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

"""
State-transition tests for a storage reservation's size and window.

The storage reservation records the default size of an auto-created data
storage resource, and its preparing, available and releasing windows are derived
from the compute reservation. The test reads the broker database directly (via
the ``database`` fixture) to verify the stored columns rather than only the API
response.
"""

from calycopis_openapi_client.models import (
    ComponentMetadata,
    DockerImageSpec,
    ExecutionRequest,
    OfferSetResponse,
    RequestedScheduleBlock,
    RequestedScheduleItem,
    SimpleComputeCores,
    SimpleComputeMemory,
)
from calycopis_openapi_client.wrappers import (
    DockerContainer,
    SimpleComputeResource,
    SimpleDataResource,
)


CANTLIEI_IMAGE = "ghcr.io/zarquan/heliophorus-cantliei:sha-831ee57"
CANTLIEI_DIGEST = "sha256:6e495692cc6f1cae2023f261f433d4691aa70b19416730f8301e45fbb74bc526"


def _make_executable(name):
    """Create a DockerContainer executable (not run unless accepted)."""
    return DockerContainer(
        meta=ComponentMetadata(name=f"{name}-exec"),
        image=DockerImageSpec(
            locations=[CANTLIEI_IMAGE],
            digest=CANTLIEI_DIGEST,
        ),
        command=["1", "0"],
    )


def _make_request(name):
    """A compute and HTTP data request with auto-created storage."""
    return ExecutionRequest(
        executable=_make_executable(name),
        compute=SimpleComputeResource(
            meta=ComponentMetadata(name=f"{name}-compute"),
            cores=SimpleComputeCores(min=1, max=1),
            memory=SimpleComputeMemory(min=1, max=1),
        ),
        data=[
            SimpleDataResource(
                meta=ComponentMetadata(name=f"{name}-data"),
                location="https://example.org/data/test-file.fits",
            ),
        ],
        schedule=RequestedScheduleBlock(
            requested=RequestedScheduleItem(
                duration="PT600S",
            ),
        ),
    )


def test_auto_storage_records_default_size_and_compute_window(client, database):
    """
    Auto-created storage is 1 GiB and its stored hull covers the compute hull.
    """
    response = client.submit_execution(
        _make_request("states-storage-size-window"),
        follow_redirect=True,
    )
    assert isinstance(response, OfferSetResponse), (
        f"Expected OfferSetResponse, got {type(response)}"
    )
    assert response.result == "YES", (
        f"Expected YES, got {response.result}. "
        f"Messages: {response.meta.messages if response.meta else 'none'}"
    )
    assert response.offers is not None
    assert len(response.offers) > 0

    offer = response.offers[0]
    assert offer.storage is not None
    assert len(offer.storage) == 1, (
        f"Expected one storage resource, got {len(offer.storage)}"
    )
    storage_response = offer.storage[0]
    assert storage_response.size is not None, (
        "The storage response should carry a size"
    )
    assert storage_response.size.min == 1, (
        f"Expected default minimum size 1, got {storage_response.size.min}"
    )
    assert storage_response.size.max == 1, (
        f"Expected default maximum size 1, got {storage_response.size.max}"
    )

    session_uuid = str(offer.meta.uuid)
    storage = database.storage_reservation(session_uuid)
    assert storage is not None, "The session should have a stored storage reservation"

    (
        min_requested,
        max_requested,
        min_offered,
        max_offered,
        prepare_start,
        prepare_duration,
        available_start,
        available_start_duration,
        available_duration,
        release_start,
        release_duration,
    ) = storage

    assert (min_requested, max_requested, min_offered, max_offered) == (1, 1, 1, 1), (
        "Auto-created storage should store the default 1/1 GiB size"
    )
    assert prepare_start > 0, "Storage prepare start should be an absolute instant"
    assert available_start > 0, "Storage available start should be an absolute instant"
    assert release_start > 0, "Storage release start should be an absolute instant"
    assert available_duration > 0, "Storage availability should cover a positive window"

    compute = database.compute_reservation_schedule(session_uuid)
    assert compute is not None, "The session should have a stored compute reservation"
    (
        compute_prepare_start,
        compute_prepare_duration,
        compute_available_start,
        compute_available_duration,
        compute_release_start,
        compute_release_duration,
    ) = compute

    data_durations = database.data_prepare_durations(session_uuid)
    assert data_durations, "The session should have a stored data resource"
    max_data_prepare = max(data_durations)

    assert prepare_start <= compute_prepare_start, (
        "Storage should prepare before compute"
    )
    assert available_start + max_data_prepare <= compute_prepare_start, (
        "Storage should be available before every data resource prepares"
    )
    assert available_start + available_duration >= (
        compute_available_start + compute_available_duration
    ), (
        "Storage availability should cover compute execution"
    )
    assert release_start + release_duration >= (
        compute_release_start + compute_release_duration
    ), (
        "Storage release should end after compute release"
    )
