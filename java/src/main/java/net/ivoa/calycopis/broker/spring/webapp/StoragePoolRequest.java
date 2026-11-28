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

/**
 * The request body for creating a storage pool.
 *
 * Pools are off-schema, so this record is written by hand rather than generated
 * from the OpenAPI schema.
 *
 * The name is required and is trimmed before the duplicate check. The
 * description is optional and copied verbatim. The storage class is optional:
 * an absent or blank value means the default class, while an unknown value is
 * rejected rather than silently changed. The controller owns those checks, so
 * the record stays a plain data carrier.
 *
 */
public record StoragePoolRequest(
    String name,
    String description,
    String storageClass
    )
    {
    }
