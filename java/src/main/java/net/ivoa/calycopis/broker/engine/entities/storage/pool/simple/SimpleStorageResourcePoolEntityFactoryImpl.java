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
 *     "timestamp": "2026-11-28T03:00:00",
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

package net.ivoa.calycopis.broker.engine.entities.storage.pool.simple;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import net.ivoa.calycopis.broker.engine.entities.storage.pool.StorageClass;
import net.ivoa.calycopis.broker.engine.functional.factory.FactoryBaseImpl;

/**
 * The framework-neutral implementation of the storage pool factory.
 *
 * Spring supplies the repository through a subclass in spring/pools/, the same
 * way the compute pool and identity factories are wired.
 *
 */
@Slf4j
public class SimpleStorageResourcePoolEntityFactoryImpl
extends FactoryBaseImpl
implements SimpleStorageResourcePoolEntityFactory
    {
    protected final SimpleStorageResourcePoolEntityRepository repository;

    public SimpleStorageResourcePoolEntityFactoryImpl(
        final SimpleStorageResourcePoolEntityRepository repository
        ){
        super();
        this.repository = repository;
        }

    @Override
    public Optional<SimpleStorageResourcePoolEntity> select(
        final UUID uuid
        ){
        return this.repository.findById(
            uuid
            );
        }

    @Override
    public Optional<SimpleStorageResourcePoolEntity> findByName(
        final String name
        ){
        return this.repository.findByName(
            name
            );
        }

    @Override
    public List<SimpleStorageResourcePoolEntity> list()
        {
        return this.repository.findAll();
        }

    @Override
    public SimpleStorageResourcePoolEntity create(
        final String name,
        final String description,
        final StorageClass storageClass
        ){
        return this.create(
            name,
            description,
            0,
            true,
            storageClass
            );
        }

    @Override
    public SimpleStorageResourcePoolEntity create(
        final String name,
        final String description,
        final int priority,
        final boolean enabled,
        final StorageClass storageClass
        ){
        log.debug(
            "Creating storage pool [{}] with class [{}], priority [{}] and enabled [{}]",
            name,
            storageClass,
            priority,
            enabled
            );
        return this.repository.save(
            new SimpleStorageResourcePoolEntity(
                name,
                description,
                priority,
                enabled,
                storageClass
                )
            );
        }

    }
