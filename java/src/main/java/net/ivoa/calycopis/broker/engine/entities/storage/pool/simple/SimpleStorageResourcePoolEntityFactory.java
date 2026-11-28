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

import net.ivoa.calycopis.broker.engine.entities.storage.pool.StorageClass;

/**
 * Creates and selects storage pools.
 *
 * Unlike the factories for the schema types, this one takes plain values rather
 * than a validator result: a pool is an admin resource with no Ivoa bean to
 * validate.
 *
 */
public interface SimpleStorageResourcePoolEntityFactory
    {
    /**
     * Select a pool by its identifier.
     *
     */
    public Optional<SimpleStorageResourcePoolEntity> select(
        final UUID uuid
        );

    /**
     * Find a pool by its name, which is the key the bootstrap uses.
     *
     */
    public Optional<SimpleStorageResourcePoolEntity> findByName(
        final String name
        );

    /**
     * List every pool, in the order the database returns them.
     *
     */
    public List<SimpleStorageResourcePoolEntity> list();

    /**
     * Create and persist a pool with the default offer settings.
     *
     */
    public SimpleStorageResourcePoolEntity create(
        final String name,
        final String description,
        final StorageClass storageClass
        );

    /**
     * Create and persist a pool with its offer settings.
     *
     */
    public SimpleStorageResourcePoolEntity create(
        final String name,
        final String description,
        final int priority,
        final boolean enabled,
        final StorageClass storageClass
        );

    }
