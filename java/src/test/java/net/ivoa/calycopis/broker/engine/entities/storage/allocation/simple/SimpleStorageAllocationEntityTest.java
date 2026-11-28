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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.ivoa.calycopis.broker.engine.entities.storage.pool.StorageClass;
import net.ivoa.calycopis.broker.engine.entities.storage.pool.simple.SimpleStorageResourcePoolEntity;

/**
 * Unit tests for the storage allocation entity validation and accessors.
 *
 */
public class SimpleStorageAllocationEntityTest
    {
    private static final Instant START = Instant.parse("2026-12-01T00:00:00Z");

    private static final Instant END = Instant.parse("2026-12-02T00:00:00Z");

    /**
     * A valid allocation carries its pool, size and window.
     *
     */
    @Test
    public void testValidAllocationCarriesItsValues()
        {
        SimpleStorageResourcePoolEntity pool = new SimpleStorageResourcePoolEntity(
            "archive",
            "The archive pool.",
            StorageClass.VOLUME
            );
        SimpleStorageAllocationEntity allocation = new SimpleStorageAllocationEntity(
            pool,
            512L,
            START,
            END
            );
        Assertions.assertSame(
            pool,
            allocation.getPool(),
            "The allocation must keep its pool"
            );
        Assertions.assertEquals(
            512L,
            allocation.getSize(),
            "The allocation must keep its size"
            );
        Assertions.assertEquals(
            START,
            allocation.getStart(),
            "The allocation must keep its start"
            );
        Assertions.assertEquals(
            END,
            allocation.getEnd(),
            "The allocation must keep its end"
            );
        }

    /**
     * An allocation with no end is open ended.
     *
     */
    @Test
    public void testAllocationWithoutEndIsOpenEnded()
        {
        SimpleStorageAllocationEntity allocation = new SimpleStorageAllocationEntity(
            new SimpleStorageResourcePoolEntity(
                "archive",
                "The archive pool.",
                StorageClass.VOLUME
                ),
            512L,
            START,
            null
            );
        Assertions.assertNull(
            allocation.getEnd(),
            "An allocation with no end must be open ended"
            );
        }

    /**
     * A zero or negative size is refused.
     *
     */
    @Test
    public void testNonPositiveSizeIsRefused()
        {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new SimpleStorageAllocationEntity(
                new SimpleStorageResourcePoolEntity(
                    "archive",
                    "The archive pool.",
                    StorageClass.VOLUME
                    ),
                0L,
                START,
                null
                ),
            "A storage allocation must reject a zero size"
            );
        }

    /**
     * A missing start is refused.
     *
     */
    @Test
    public void testMissingStartIsRefused()
        {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new SimpleStorageAllocationEntity(
                new SimpleStorageResourcePoolEntity(
                    "archive",
                    "The archive pool.",
                    StorageClass.VOLUME
                    ),
                512L,
                null,
                null
                ),
            "A storage allocation must reject a missing start"
            );
        }

    /**
     * An end that is not after the start is refused.
     *
     */
    @Test
    public void testEndMustBeAfterStart()
        {
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new SimpleStorageAllocationEntity(
                new SimpleStorageResourcePoolEntity(
                    "archive",
                    "The archive pool.",
                    StorageClass.VOLUME
                    ),
                512L,
                START,
                START
                ),
            "A storage allocation must reject an end equal to its start"
            );
        Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new SimpleStorageAllocationEntity(
                new SimpleStorageResourcePoolEntity(
                    "archive",
                    "The archive pool.",
                    StorageClass.VOLUME
                    ),
                512L,
                START,
                START.minusSeconds(1L)
                ),
            "A storage allocation must reject an end before its start"
            );
        }

    /**
     * An update replaces the size and window.
     *
     */
    @Test
    public void testUpdateReplacesTheValues()
        {
        SimpleStorageAllocationEntity allocation = new SimpleStorageAllocationEntity(
            new SimpleStorageResourcePoolEntity(
                "archive",
                "The archive pool.",
                StorageClass.VOLUME
                ),
            512L,
            START,
            null
            );
        allocation.update(
            1024L,
            START.plusSeconds(60L),
            END
            );
        Assertions.assertEquals(
            1024L,
            allocation.getSize(),
            "An update must replace the size"
            );
        Assertions.assertEquals(
            START.plusSeconds(60L),
            allocation.getStart(),
            "An update must replace the start"
            );
        Assertions.assertEquals(
            END,
            allocation.getEnd(),
            "An update must replace the end"
            );
        }

    }
