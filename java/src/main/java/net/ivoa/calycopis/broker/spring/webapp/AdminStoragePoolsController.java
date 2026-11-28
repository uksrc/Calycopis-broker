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

package net.ivoa.calycopis.broker.spring.webapp;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

import net.ivoa.calycopis.broker.engine.entities.storage.pool.StorageClass;
import net.ivoa.calycopis.broker.engine.entities.storage.pool.simple.SimpleStorageResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.pool.simple.SimpleStorageResourcePoolEntityFactory;
import net.ivoa.calycopis.broker.spring.webapp.error.BrokerProblemException;
import net.ivoa.calycopis.broker.spring.webapp.error.ProblemType;

/**
 * Admin endpoints for storage pools.
 *
 * The path carries the resource type as a segment, mirroring the compute pools
 * at /admin/pools/compute. Pools are off-schema, so the request and response
 * bodies are hand-written records rather than generated beans.
 *
 * This stage creates and reads pools. The update verb, ordering and policies
 * arrive with the later storage pool stages.
 *
 */
@Slf4j
@RestController
@RequestMapping("/admin/pools/storage")
public class AdminStoragePoolsController
    {
    private final SimpleStorageResourcePoolEntityFactory poolFactory;

    public AdminStoragePoolsController(
        final SimpleStorageResourcePoolEntityFactory poolFactory
        ){
        this.poolFactory = poolFactory;
        }

    /**
     * Create a storage pool.
     *
     */
    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
        )
    public ResponseEntity<StoragePoolResponse> create(
        @RequestBody final StoragePoolRequest request
        ){
        String name = request.name();
        if (name == null || name.isBlank())
            {
            throw new BrokerProblemException(
                ProblemType.INVALID_STORAGE_POOL,
                "A storage pool needs a name."
                );
            }
        name = name.trim();
        if (this.poolFactory.findByName(name).isPresent())
            {
            throw new BrokerProblemException(
                ProblemType.STORAGE_POOL_NAME_IN_USE,
                "A storage pool named [" + name + "] already exists."
                );
            }

        StorageClass storageClass = StorageClass.DEFAULT;
        String requestedClass = request.storageClass();
        if (requestedClass != null && !requestedClass.isBlank())
            {
            Optional<StorageClass> parsed = StorageClass.parse(
                requestedClass
                );
            if (parsed.isEmpty())
                {
                throw new BrokerProblemException(
                    ProblemType.INVALID_STORAGE_POOL,
                    "Unknown storage class [" + requestedClass + "]. Use VOLUME or BIND."
                    );
                }
            storageClass = parsed.get();
            }

        SimpleStorageResourcePoolEntity entity = this.poolFactory.create(
            name,
            request.description(),
            storageClass
            );
        log.info(
            "Created storage pool [{}] [{}] with class [{}]",
            entity.getName(),
            entity.getUuid(),
            entity.getStorageClass()
            );
        return ResponseEntity.created(
            URI.create("/admin/pools/storage/" + entity.getUuid())
            ).body(
                response(entity)
                );
        }

    /**
     * List the storage pools.
     *
     */
    @GetMapping(
        produces = MediaType.APPLICATION_JSON_VALUE
        )
    public ResponseEntity<List<StoragePoolResponse>> list()
        {
        List<StoragePoolResponse> responses = new ArrayList<>();
        for (SimpleStorageResourcePoolEntity entity : this.poolFactory.list())
            {
            responses.add(
                response(entity)
                );
            }
        return ResponseEntity.ok(
            responses
            );
        }

    /**
     * Select one storage pool.
     *
     * The identifier arrives as a string so that a malformed one is a 400 rather
     * than a Spring type mismatch, which the generic handler would turn into a
     * 500.
     *
     */
    @GetMapping(
        path = "/{uuid}",
        produces = MediaType.APPLICATION_JSON_VALUE
        )
    public ResponseEntity<StoragePoolResponse> select(
        @PathVariable("uuid") final String uuid
        ){
        UUID identifier = parseUuid(
            uuid
            );
        Optional<SimpleStorageResourcePoolEntity> entity = this.poolFactory.select(
            identifier
            );
        if (entity.isEmpty())
            {
            throw new BrokerProblemException(
                ProblemType.STORAGE_POOL_NOT_FOUND,
                "No storage pool with identifier [" + uuid + "]."
                );
            }
        return ResponseEntity.ok(
            response(
                entity.get()
                )
            );
        }

    /**
     * Parse a UUID, refusing anything else with a 400.
     *
     */
    private static UUID parseUuid(final String uuid)
        {
        try {
            return UUID.fromString(
                uuid
                );
            }
        catch (IllegalArgumentException exception)
            {
            throw new BrokerProblemException(
                ProblemType.INVALID_STORAGE_POOL,
                "The storage pool identifier [" + uuid + "] is not a UUID."
                );
            }
        }

    /**
     * Map an entity onto the response body.
     *
     */
    private static StoragePoolResponse response(final SimpleStorageResourcePoolEntity entity)
        {
        return new StoragePoolResponse(
            entity.getUuid(),
            entity.getName(),
            entity.getDescription(),
            entity.getPriority(),
            entity.isEnabled(),
            entity.getStorageClass(),
            entity.getCreated()
            );
        }

    }
