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

package net.ivoa.calycopis.broker.engine.functional.booking.storage.simple;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the storage allocation query builder.
 *
 * These tests inspect the SQL rather than execute it against PostgreSQL. They
 * pin the block arithmetic, the pool scope, the reservation size source and the
 * absence of a storage-class predicate.
 *
 */
public class SimpleStorageAllocationQueryTest
    {
    private static final UUID POOL = UUID.fromString(
        "11111111-1111-4111-8111-111111111111"
        );

    private static final UUID EXCLUDED = UUID.fromString(
        "22222222-2222-4222-8222-222222222222"
        );

    /**
     * The query constant must exist and be non-empty.
     *
     */
    @Test
    public void testDatabaseQueryConstantExists()
        {
        Assertions.assertNotNull(
            SimpleStorageAllocationQuery.DATABASE_QUERY
            );
        Assertions.assertFalse(
            SimpleStorageAllocationQuery.DATABASE_QUERY.isEmpty()
            );
        }

    /**
     * Build a query with explicit values and check that every placeholder has
     * been substituted.
     *
     */
    @Test
    public void testBuildSubstitutesAllParameters()
        {
        String query = SimpleStorageAllocationQuery.build(
            POOL,
            EXCLUDED,
            128L,
            Instant.parse("2026-12-01T00:00:00Z"),
            Instant.parse("2026-12-02T00:00:00Z")
            );
        for (String placeholder : new String[] {
            ":blockstep",
            ":pool",
            ":excluded",
            ":size",
            ":startseconds",
            ":endseconds"
            })
            {
            Assertions.assertFalse(
                query.contains(placeholder),
                "Query still contains unreplaced placeholder [" + placeholder + "]"
                );
            }
        Assertions.assertTrue(
            query.contains("COUNT(*) AS OverCommittedBlocks"),
            "Query must end with the over-committed block count"
            );
        }

    /**
     * The supply must be the sum of the pool's allocations.
     *
     */
    @Test
    public void testBuildReadsSupplyFromAllocations()
        {
        String query = SimpleStorageAllocationQuery.build(
            POOL,
            EXCLUDED,
            128L,
            Instant.parse("2026-12-01T00:00:00Z"),
            null
            );
        Assertions.assertTrue(
            query.contains("AbstractStorageAllocations"),
            "Query must read the storage allocations"
            );
        Assertions.assertTrue(
            query.contains("SUM(Allocations.size)"),
            "Supply must be the sum of the pool's allocation sizes"
            );
        Assertions.assertFalse(
            query.contains(":totalsize"),
            "Query must not read a global storage total"
            );
        }

    /**
     * The demand must come from the live storage reservations.
     *
     */
    @Test
    public void testBuildReadsLiveStorageReservations()
        {
        String query = SimpleStorageAllocationQuery.build(
            POOL,
            EXCLUDED,
            128L,
            Instant.parse("2026-12-01T00:00:00Z"),
            null
            );
        Assertions.assertTrue(
            query.contains("AbstractStorageReservations"),
            "Query must read the storage reservations"
            );
        Assertions.assertTrue(
            query.contains("SimpleStorageReservations.maxofferedsize"),
            "Query must prefer the offered storage size"
            );
        Assertions.assertTrue(
            query.contains("SimpleStorageReservations.maxrequestedsize"),
            "Query must fall back to the requested storage size"
            );
        Assertions.assertTrue(
            query.contains("'OFFERED', 'ACCEPTED', 'WAITING', 'PREPARING', 'AVAILABLE', 'RUNNING', 'RELEASING'"),
            "Query must use the live session phases"
            );
        }

    /**
     * Overlapping reservations and allocations must be summed per block.
     *
     */
    @Test
    public void testBuildSumsOverlappingBlocks()
        {
        String query = SimpleStorageAllocationQuery.build(
            POOL,
            EXCLUDED,
            128L,
            Instant.parse("2026-12-01T00:00:00Z"),
            null
            );
        Assertions.assertTrue(
            query.contains("generate_series"),
            "Query must expand each reservation over its blocks"
            );
        Assertions.assertTrue(
            query.contains("SUM(ReservedBlocks.UsedStorage)"),
            "Reservations sharing a block must be summed"
            );
        Assertions.assertTrue(
            query.contains("GROUP BY"),
            "The block sum must group by block start"
            );
        }

    /**
     * An open-ended allocation must cover every block from its start, and an
     * allocation end must round up to the block that contains it.
     *
     */
    @Test
    public void testBuildTreatsMissingEndAsOpenEnded()
        {
        String query = SimpleStorageAllocationQuery.build(
            POOL,
            EXCLUDED,
            128L,
            Instant.parse("2026-12-01T00:00:00Z"),
            null
            );
        Assertions.assertTrue(
            query.contains("Allocations.endseconds IS NULL"),
            "An allocation with no end must cover every block from its start"
            );
        Assertions.assertTrue(
            query.contains("(60 - 1)"),
            "An allocation end must round up to the block that contains it"
            );
        Assertions.assertTrue(
            query.contains("(NULL IS NULL OR"),
            "The built query must carry the literal NULL end in the candidate window"
            );
        }

    /**
     * The allocations must be scoped to one pool.
     *
     */
    @Test
    public void testBuildScopesAllocationsToThePool()
        {
        String query = SimpleStorageAllocationQuery.build(
            POOL,
            EXCLUDED,
            128L,
            Instant.parse("2026-12-01T00:00:00Z"),
            null
            );
        Assertions.assertTrue(
            query.contains("Allocations.pool = '" + POOL + "'"),
            "The allocations must be filtered to the pool being changed"
            );
        }

    /**
     * The class must not alter the query.
     *
     */
    @Test
    public void testBuildHasNoClassPredicate()
        {
        String query = SimpleStorageAllocationQuery.build(
            POOL,
            EXCLUDED,
            128L,
            Instant.parse("2026-12-01T00:00:00Z"),
            null
            );
        Assertions.assertFalse(
            query.toLowerCase().contains("storageclass"),
            "The allocation query must not filter or branch on storage class"
            );
        }

    }
