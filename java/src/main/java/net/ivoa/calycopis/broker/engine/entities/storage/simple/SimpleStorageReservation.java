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
 *     "interval": "2026-11-27T18:30:00/2026-11-27T18:41:01",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 36,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.entities.storage.simple;

import java.net.URI;

import net.ivoa.calycopis.broker.engine.entities.storage.AbstractStorageReservation;

/**
 * Public interface for a SimpleStorageReservation.
 *
 */
public interface SimpleStorageReservation
extends AbstractStorageReservation
    {
    
    /**
     * The OpenAPI type identifier.
     * 
     */
    public static final URI KIND_DISCRIMINATOR = URI.create("https://www.purl.org/ivoa.net/Calycopis-openapi/schema/v1.0/kinds/storage/simple-storage-resource.yaml") ;

    /**
     * The minimum size requested for this storage reservation, in GiB.
     *
     */
    public Long getMinRequestedSize();

    /**
     * The maximum size requested for this storage reservation, in GiB.
     *
     */
    public Long getMaxRequestedSize();

    /**
     * The minimum size offered for this storage reservation, in GiB.
     *
     */
    public Long getMinOfferedSize();

    /**
     * The maximum size offered for this storage reservation, in GiB.
     *
     */
    public Long getMaxOfferedSize();

    }

