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

package net.ivoa.calycopis.broker.engine.entities.storage.allocation;

import java.net.URI;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.extern.slf4j.Slf4j;

import net.ivoa.calycopis.broker.engine.entities.component.ComponentEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.pool.AbstractStorageResourcePoolEntity;

/**
 * The abstract tier of the storage allocation entity family.
 *
 * The pool reference, the window and the size sit here because every
 * allocation type supplies capacity in the same way. A second allocation type
 * adds a subclass under this root, not a second root.
 *
 * The window is stored in epoch seconds, matching the lifecycle components and
 * the booking query, and exposed as Instant. An open-ended allocation has no
 * end seconds and covers every block from its start onwards.
 *
 * The size is positive and an end is after its start. The entity enforces both,
 * so a value that reached it from a path that forgot to validate fails here
 * rather than being stored.
 *
 */
@Slf4j
@Entity
@Table(
    name = "abstractstorageallocations"
    )
@Inheritance(
    strategy = InheritanceType.JOINED
    )
public abstract class AbstractStorageAllocationEntity
extends ComponentEntity
implements AbstractStorageAllocation
    {

    /**
     * Protected no-arg constructor required by JPA.
     *
     */
    protected AbstractStorageAllocationEntity()
        {
        super();
        }

    /**
     * Protected constructor used by our factories.
     *
     */
    protected AbstractStorageAllocationEntity(
        final AbstractStorageResourcePoolEntity pool,
        final long size,
        final Instant start,
        final Instant end
        ){
        super(
            null,
            null,
            Instant.now(),
            null
            );
        this.pool = pool;
        this.setValues(
            size,
            start,
            end
            );
        }

    @ManyToOne(
        optional = false,
        fetch = FetchType.LAZY
        )
    @JoinColumn(
        name = "pool",
        referencedColumnName = "uuid",
        nullable = false
        )
    private AbstractStorageResourcePoolEntity pool;

    @Override
    public AbstractStorageResourcePoolEntity getPool()
        {
        return this.pool;
        }

    @Column(
        name = "startseconds",
        nullable = false
        )
    private long startseconds;

    @Override
    public Instant getStart()
        {
        return Instant.ofEpochSecond(
            this.startseconds
            );
        }

    @Column(
        name = "endseconds",
        nullable = true
        )
    private Long endseconds;

    @Override
    public Instant getEnd()
        {
        if (this.endseconds == null)
            {
            return null;
            }
        else {
            return Instant.ofEpochSecond(
                this.endseconds.longValue()
                );
            }
        }

    @Column(
        name = "size",
        nullable = false
        )
    private long size;

    @Override
    public long getSize()
        {
        return this.size;
        }

    /**
     * Change the size and the window of this allocation.
     *
     * The pool is not changed: moving an allocation between pools would need a
     * guard against both pools, and is deferred.
     *
     */
    public void update(
        final long size,
        final Instant start,
        final Instant end
        ){
        this.setValues(
            size,
            start,
            end
            );
        }

    /**
     * Validate and store the size and the window.
     *
     */
    private void setValues(
        final long size,
        final Instant start,
        final Instant end
        ){
        if (start == null)
            {
            throw new IllegalArgumentException(
                "A storage allocation needs a start instant."
                );
            }
        if (size <= 0L)
            {
            throw new IllegalArgumentException(
                "A storage allocation needs a positive size, not [" + size + "]."
                );
            }
        if (end != null && !end.isAfter(start))
            {
            throw new IllegalArgumentException(
                "A storage allocation must end after it starts."
                );
            }
        this.size = size;
        this.startseconds = start.getEpochSecond();
        if (end == null)
            {
            this.endseconds = null;
            }
        else {
            this.endseconds = Long.valueOf(
                end.getEpochSecond()
                );
            }
        }

    @Override
    protected URI getWebappPath()
        {
        return URI.create("/allocations/storage/");
        }

    }
