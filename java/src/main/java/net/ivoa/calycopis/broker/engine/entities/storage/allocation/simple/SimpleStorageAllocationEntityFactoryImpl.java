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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import net.ivoa.calycopis.broker.engine.entities.storage.pool.AbstractStorageResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.functional.booking.storage.simple.SimpleStorageAllocationQuery;
import net.ivoa.calycopis.broker.engine.functional.booking.storage.simple.SimpleStorageAllocationQueryHandler;
import net.ivoa.calycopis.broker.engine.functional.factory.FactoryBaseImpl;

/**
 * The framework-neutral implementation of the storage allocation factory.
 *
 * Spring supplies the repository and the query handler through a subclass in
 * spring/pools/, the same way the compute allocation factory is wired.
 *
 * The over-commit check is domain logic, so it lives here rather than in the
 * controller: it reads the live reservations and the pool's supply through the
 * query handler, and the caller decides what a conflict means.
 *
 */
@Slf4j
public class SimpleStorageAllocationEntityFactoryImpl
extends FactoryBaseImpl
implements SimpleStorageAllocationEntityFactory
    {
    protected final SimpleStorageAllocationEntityRepository repository;

    protected final SimpleStorageAllocationQueryHandler queryHandler;

    public SimpleStorageAllocationEntityFactoryImpl(
        final SimpleStorageAllocationEntityRepository repository,
        final SimpleStorageAllocationQueryHandler queryHandler
        ){
        super();
        this.repository = repository;
        this.queryHandler = queryHandler;
        }

    @Override
    public Optional<SimpleStorageAllocationEntity> select(
        final UUID uuid
        ){
        return this.repository.findById(
            uuid
            );
        }

    @Override
    public List<SimpleStorageAllocationEntity> list(
        final AbstractStorageResourcePoolEntity pool
        ){
        return this.repository.findByPool(
            pool
            );
        }

    @Override
    public SimpleStorageAllocationEntity create(
        final AbstractStorageResourcePoolEntity pool,
        final long size,
        final Instant start,
        final Instant end
        ){
        log.debug(
            "Creating storage allocation for pool [{}]",
            pool.getUuid()
            );
        return this.repository.save(
            new SimpleStorageAllocationEntity(
                pool,
                size,
                start,
                end
                )
            );
        }

    @Override
    public SimpleStorageAllocationEntity update(
        final SimpleStorageAllocationEntity entity,
        final long size,
        final Instant start,
        final Instant end
        ){
        log.debug(
            "Updating storage allocation [{}]",
            entity.getUuid()
            );
        entity.update(
            size,
            start,
            end
            );
        return this.repository.save(
            entity
            );
        }

    @Override
    public void delete(
        final SimpleStorageAllocationEntity entity
        ){
        log.debug(
            "Deleting storage allocation [{}]",
            entity.getUuid()
            );
        this.repository.delete(
            entity
            );
        }

    @Override
    public boolean wouldOverCommit(
        final UUID poolUuid,
        final UUID excludedUuid,
        final long size,
        final Instant start,
        final Instant end
        ){
        String query = SimpleStorageAllocationQuery.build(
            poolUuid,
            excludedUuid,
            size,
            start,
            end
            );
        for (Long count : this.queryHandler.query(query))
            {
            if (count != null && count.longValue() > 0L)
                {
                return true;
                }
            }
        return false;
        }

    }
