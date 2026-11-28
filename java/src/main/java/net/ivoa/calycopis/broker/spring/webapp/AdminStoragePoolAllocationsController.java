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

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

import net.ivoa.calycopis.broker.engine.entities.storage.allocation.simple.SimpleStorageAllocationEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.allocation.simple.SimpleStorageAllocationEntityFactory;
import net.ivoa.calycopis.broker.engine.entities.storage.pool.simple.SimpleStorageResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.pool.simple.SimpleStorageResourcePoolEntityFactory;
import net.ivoa.calycopis.broker.engine.entities.storage.simple.SimpleStorageReservation;
import net.ivoa.calycopis.broker.engine.functional.booking.BookingTransactionHandler;
import net.ivoa.calycopis.broker.spring.webapp.error.BrokerProblemException;
import net.ivoa.calycopis.broker.spring.webapp.error.ProblemType;

/**
 * Admin endpoints for the allocations of one storage pool.
 *
 * The collection path carries the pool identifier, which is the one UUID in
 * these URLs; an individual allocation is addressed by its own identifier at
 * /admin/allocations/storage/{uuid} instead. Nothing here has two UUIDs in one
 * path.
 *
 * A creation redirects to the allocation details, so a client does not have to
 * read the body to find the new identifier.
 *
 */
@Slf4j
@RestController
@RequestMapping("/admin/pools/storage/{poolUuid}/allocations")
public class AdminStoragePoolAllocationsController
    {
    private final SimpleStorageResourcePoolEntityFactory poolFactory;

    private final SimpleStorageAllocationEntityFactory allocationFactory;

    private final BookingTransactionHandler bookingTransactionHandler;

    /**
     * No allocation has this identifier, so excluding it leaves every stored
     * allocation in the supply when a new allocation is checked.
     *
     */
    private static final UUID NO_ALLOCATION = new UUID(0L, 0L);

    public AdminStoragePoolAllocationsController(
        final SimpleStorageResourcePoolEntityFactory poolFactory,
        final SimpleStorageAllocationEntityFactory allocationFactory,
        final BookingTransactionHandler bookingTransactionHandler
        ){
        this.poolFactory = poolFactory;
        this.allocationFactory = allocationFactory;
        this.bookingTransactionHandler = bookingTransactionHandler;
        }

    /**
     * List the allocations of one pool.
     *
     */
    @GetMapping(
        produces = MediaType.APPLICATION_JSON_VALUE
        )
    public ResponseEntity<List<StorageAllocationResponse>> list(
        @PathVariable("poolUuid") final String poolUuid
        ){
        SimpleStorageResourcePoolEntity pool = findPool(
            poolUuid
            );
        List<StorageAllocationResponse> responses = new ArrayList<>();
        for (SimpleStorageAllocationEntity entity : this.allocationFactory.list(pool))
            {
            responses.add(
                StorageAllocationResponse.of(
                    entity
                    )
                );
            }
        return ResponseEntity.ok(
            responses
            );
        }

    /**
     * Create an allocation for one pool.
     *
     */
    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
        )
    public ResponseEntity<Void> create(
        @PathVariable("poolUuid") final String poolUuid,
        @RequestBody final StorageAllocationRequest request
        ){
        SimpleStorageResourcePoolEntity pool = findPool(
            poolUuid
            );
        long size = StorageAllocationValidation.size(
            request.size()
            );
        Instant start = StorageAllocationValidation.start(
            request.start()
            );
        Instant end = StorageAllocationValidation.end(
            request.end(),
            start
            );
        SimpleStorageAllocationEntity entity = this.bookingTransactionHandler.book(
            SimpleStorageReservation.KIND_DISCRIMINATOR.toString(),
            () -> {
                if (this.allocationFactory.wouldOverCommit(
                    pool.getUuid(),
                    NO_ALLOCATION,
                    size,
                    start,
                    end
                    ))
                    {
                    throw new BrokerProblemException(
                        ProblemType.STORAGE_ALLOCATION_OVERCOMMITTED,
                        "Creating storage allocation for pool [" + poolUuid + "] would over-commit it."
                        );
                    }
                return this.allocationFactory.create(
                    pool,
                    size,
                    start,
                    end
                    );
                }
            );
        log.info(
            "Created storage allocation [{}] for pool [{}]",
            entity.getUuid(),
            pool.getUuid()
            );
        return ResponseEntity.status(
            HttpStatus.SEE_OTHER
            ).location(
                URI.create(
                    "/admin/allocations/storage/" + entity.getUuid()
                    )
                ).build();
        }

    /**
     * Resolve the pool named in the path.
     *
     */
    private SimpleStorageResourcePoolEntity findPool(final String poolUuid)
        {
        UUID identifier = parsePoolUuid(
            poolUuid
            );
        Optional<SimpleStorageResourcePoolEntity> pool = this.poolFactory.select(
            identifier
            );
        if (pool.isEmpty())
            {
            throw new BrokerProblemException(
                ProblemType.STORAGE_POOL_NOT_FOUND,
                "No storage pool with identifier [" + poolUuid + "]."
                );
            }
        return pool.get();
        }

    /**
     * Parse a pool UUID, refusing anything else with a 400.
     *
     */
    private static UUID parsePoolUuid(final String poolUuid)
        {
        try
            {
            return UUID.fromString(
                poolUuid
                );
            }
        catch (IllegalArgumentException exception)
            {
            throw new BrokerProblemException(
                ProblemType.INVALID_STORAGE_POOL,
                "The storage pool identifier [" + poolUuid + "] is not a UUID."
                );
            }
        }

    }
