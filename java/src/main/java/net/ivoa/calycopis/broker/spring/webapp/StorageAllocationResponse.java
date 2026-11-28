/*
 * <meta:header>
 *   <meta:licence>
 *     Copyright (C) 2026 University of Manchester.
 *
 *     This information is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This information is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *   </meta:licence>
 * </meta:header>
 *
 * AIMetrics: [
 *     {
 *     "interval": "2026-11-28T04:50:00/2026-11-28T05:40:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.spring.webapp;

import java.time.Instant;
import java.util.UUID;

import net.ivoa.calycopis.broker.engine.entities.storage.allocation.simple.SimpleStorageAllocationEntity;

/**
 * The response body for a storage allocation.
 *
 * Off-schema for the same reason as the storage pool bodies: allocations are
 * admin resources until the Calycopis extension is designed.
 *
 * The pool is repeated in every response because the item path identifies the
 * allocation, not the pool. There is no modified timestamp, for the same reason
 * as the pool response: nothing maintains the component's modified field yet.
 *
 */
public record StorageAllocationResponse(
    UUID uuid,
    UUID pool,
    long size,
    Instant start,
    Instant end,
    Instant created
    )
    {

    /**
     * Map an entity onto the response body.
     *
     */
    public static StorageAllocationResponse of(final SimpleStorageAllocationEntity entity)
        {
        return new StorageAllocationResponse(
            entity.getUuid(),
            entity.getPool().getUuid(),
            entity.getSize(),
            entity.getStart(),
            entity.getEnd(),
            entity.getCreated()
            );
        }

    }
