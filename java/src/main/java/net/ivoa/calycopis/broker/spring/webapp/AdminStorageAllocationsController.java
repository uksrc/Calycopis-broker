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
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

import net.ivoa.calycopis.broker.engine.entities.storage.allocation.simple.SimpleStorageAllocationEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.allocation.simple.SimpleStorageAllocationEntityFactory;
import net.ivoa.calycopis.broker.engine.entities.storage.simple.SimpleStorageReservation;
import net.ivoa.calycopis.broker.engine.functional.booking.BookingTransactionHandler;
import net.ivoa.calycopis.broker.spring.webapp.error.BrokerProblemException;
import net.ivoa.calycopis.broker.spring.webapp.error.ProblemType;

/**
 * Admin endpoints for one storage allocation.
 *
 * An allocation is addressed by its own identifier, so these paths carry one
 * UUID. Its pool is in the response rather than in the path.
 *
 * An update or a delete changes the pool's supply, which is check-then-act
 * against the live reservations. Both run inside the booking transaction, so
 * they take the same advisory lock as a storage booking and cannot commit a
 * change that a concurrent request would invalidate.
 *
 */
@Slf4j
@RestController
@RequestMapping("/admin/allocations/storage")
public class AdminStorageAllocationsController
    {
    private final SimpleStorageAllocationEntityFactory allocationFactory;

    private final BookingTransactionHandler bookingTransactionHandler;

    public AdminStorageAllocationsController(
        final SimpleStorageAllocationEntityFactory allocationFactory,
        final BookingTransactionHandler bookingTransactionHandler
        ){
        this.allocationFactory = allocationFactory;
        this.bookingTransactionHandler = bookingTransactionHandler;
        }

    /**
     * Select one allocation.
     *
     */
    @GetMapping(
        path = "/{uuid}",
        produces = MediaType.APPLICATION_JSON_VALUE
        )
    public ResponseEntity<StorageAllocationResponse> select(
        @PathVariable("uuid") final String uuid
        ){
        return ResponseEntity.ok(
            StorageAllocationResponse.of(
                find(
                    uuid
                    )
                )
            );
        }

    /**
     * Update the size and the window of one allocation.
     *
     * A change that would leave the pool with less capacity than its live
     * reservations in any block is rejected, and the allocation is left as it
     * was.
     *
     */
    @PostMapping(
        path = "/{uuid}",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
        )
    public ResponseEntity<StorageAllocationResponse> update(
        @PathVariable("uuid") final String uuid,
        @RequestBody final StorageAllocationRequest request
        ){
        UUID identifier = parseAllocationUuid(
            uuid
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
        SimpleStorageAllocationEntity updated = this.bookingTransactionHandler.book(
            SimpleStorageReservation.KIND_DISCRIMINATOR.toString(),
            () -> {
                SimpleStorageAllocationEntity entity = find(
                    identifier
                    );
                if (this.allocationFactory.wouldOverCommit(
                    entity.getPool().getUuid(),
                    entity.getUuid(),
                    size,
                    start,
                    end
                    ))
                    {
                    throw new BrokerProblemException(
                        ProblemType.STORAGE_ALLOCATION_OVERCOMMITTED,
                        "Updating storage allocation [" + uuid + "] would over-commit its pool."
                        );
                    }
                return this.allocationFactory.update(
                    entity,
                    size,
                    start,
                    end
                    );
                }
            );
        log.info(
            "Updated storage allocation [{}]",
            updated.getUuid()
            );
        return ResponseEntity.ok(
            StorageAllocationResponse.of(
                updated
                )
            );
        }

    /**
     * Delete one allocation.
     *
     * A removal that would leave the pool with less capacity than its live
     * reservations in any block is rejected, and the allocation remains.
     *
     */
    @DeleteMapping(
        path = "/{uuid}"
        )
    public ResponseEntity<Void> delete(
        @PathVariable("uuid") final String uuid
        ){
        UUID identifier = parseAllocationUuid(
            uuid
            );
        this.bookingTransactionHandler.book(
            SimpleStorageReservation.KIND_DISCRIMINATOR.toString(),
            () -> {
                SimpleStorageAllocationEntity entity = find(
                    identifier
                    );
                if (this.allocationFactory.wouldOverCommit(
                    entity.getPool().getUuid(),
                    entity.getUuid(),
                    0L,
                    Instant.EPOCH,
                    null
                    ))
                    {
                    throw new BrokerProblemException(
                        ProblemType.STORAGE_ALLOCATION_OVERCOMMITTED,
                        "Deleting storage allocation [" + uuid + "] would over-commit its pool."
                        );
                    }
                this.allocationFactory.delete(
                    entity
                    );
                return null;
                }
            );
        log.info(
            "Deleted storage allocation [{}]",
            uuid
            );
        return ResponseEntity.noContent().build();
        }

    /**
     * Resolve the allocation named in the path.
     *
     */
    private SimpleStorageAllocationEntity find(final String uuid)
        {
        return find(
            parseAllocationUuid(
                uuid
                )
            );
        }

    /**
     * Resolve the allocation named by an identifier.
     *
     */
    private SimpleStorageAllocationEntity find(final UUID identifier)
        {
        Optional<SimpleStorageAllocationEntity> entity = this.allocationFactory.select(
            identifier
            );
        if (entity.isEmpty())
            {
            throw new BrokerProblemException(
                ProblemType.STORAGE_ALLOCATION_NOT_FOUND,
                "No storage allocation with identifier [" + identifier + "]."
                );
            }
        return entity.get();
        }

    /**
     * Parse an allocation UUID, refusing anything else with a 400.
     *
     */
    private static UUID parseAllocationUuid(final String uuid)
        {
        try
            {
            return UUID.fromString(
                uuid
                );
            }
        catch (IllegalArgumentException exception)
            {
            throw new BrokerProblemException(
                ProblemType.INVALID_STORAGE_ALLOCATION,
                "The storage allocation identifier [" + uuid + "] is not a UUID."
                );
            }
        }

    }
