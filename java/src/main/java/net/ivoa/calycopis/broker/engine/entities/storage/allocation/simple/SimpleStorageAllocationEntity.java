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

package net.ivoa.calycopis.broker.engine.entities.storage.allocation.simple;

import java.net.URI;
import java.time.Instant;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.extern.slf4j.Slf4j;

import net.ivoa.calycopis.broker.engine.entities.storage.allocation.AbstractStorageAllocationEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.pool.AbstractStorageResourcePoolEntity;

/**
 * The concrete storage allocation entity.
 *
 * It carries nothing beyond the abstract tier for now: the pool, the window and
 * the size are common to every allocation type. A future allocation type adds a
 * subclass of the abstract entity, not a column here.
 *
 */
@Slf4j
@Entity
@Table(
    name = "simplestorageallocations"
    )
@DiscriminatorValue(
    value = "uri:storage-allocation"
    )
public class SimpleStorageAllocationEntity
extends AbstractStorageAllocationEntity
implements SimpleStorageAllocation
    {
    /**
     * Protected no-arg constructor required by JPA.
     *
     */
    protected SimpleStorageAllocationEntity()
        {
        super();
        }

    /**
     * Protected constructor used by our factories.
     *
     */
    public SimpleStorageAllocationEntity(
        final AbstractStorageResourcePoolEntity pool,
        final long size,
        final Instant start,
        final Instant end
        ){
        super(
            pool,
            size,
            start,
            end
            );
        }

    @Override
    public URI getKind()
        {
        return SimpleStorageAllocation.KIND_DISCRIMINATOR;
        }

    }
