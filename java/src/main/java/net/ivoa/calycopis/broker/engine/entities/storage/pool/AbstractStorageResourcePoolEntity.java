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

package net.ivoa.calycopis.broker.engine.entities.storage.pool;

import java.net.URI;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

import lombok.extern.slf4j.Slf4j;

import net.ivoa.calycopis.broker.engine.entities.component.ComponentEntity;

/**
 * The abstract tier of the storage pool entity family.
 *
 * The root table exists because the family is mapped with a joined inheritance
 * strategy, even though there is one concrete type today. A second pool
 * implementation adds an entity under this one, not a second root.
 *
 * A pool has no owner, because the admin credential in admin.yaml is a
 * configuration setting rather than an identity entity. The owner column stays
 * null and the API does not report one.
 *
 */
@Slf4j
@Entity
@Table(
    name = "abstractstorageresourcepools"
    )
@Inheritance(
    strategy = InheritanceType.JOINED
    )
public abstract class AbstractStorageResourcePoolEntity
extends ComponentEntity
implements AbstractStorageResourcePool
    {
    /**
     * Protected no-arg constructor required by JPA.
     *
     */
    protected AbstractStorageResourcePoolEntity()
        {
        super();
        }

    /**
     * Protected constructor used by our factories.
     *
     */
    protected AbstractStorageResourcePoolEntity(
        final String name,
        final String description,
        final StorageClass storageClass
        ){
        this(
            name,
            description,
            0,
            true,
            storageClass
            );
        }

    /**
     * Protected constructor used by our factories.
     *
     */
    protected AbstractStorageResourcePoolEntity(
        final String name,
        final String description,
        final int priority,
        final boolean enabled,
        final StorageClass storageClass
        ){
        super(
            name,
            description,
            Instant.now(),
            null
            );
        this.priority = priority;
        this.enabled = enabled;
        if (storageClass == null)
            {
            this.storageClass = StorageClass.DEFAULT;
            }
        else {
            this.storageClass = storageClass;
            }
        }

    @Column(
        name = "priority",
        nullable = false
        )
    private int priority = 0;

    @Override
    public int getPriority()
        {
        return this.priority;
        }

    public void setPriority(final int priority)
        {
        this.priority = priority;
        }

    @Column(
        name = "enabled",
        nullable = false
        )
    private boolean enabled = true;

    @Override
    public boolean isEnabled()
        {
        return this.enabled;
        }

    public void setEnabled(final boolean enabled)
        {
        this.enabled = enabled;
        }

    @Enumerated(
        EnumType.STRING
        )
    @Column(
        name = "storageclass",
        nullable = false
        )
    private StorageClass storageClass = StorageClass.DEFAULT;

    @Override
    public StorageClass getStorageClass()
        {
        return this.storageClass;
        }

    @Override
    protected URI getWebappPath()
        {
        return URI.create("/pools/storage/");
        }

    }
