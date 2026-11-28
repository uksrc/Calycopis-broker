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

import net.ivoa.calycopis.broker.engine.entities.component.Component;

/**
 * A named source of storage capacity for one storage class.
 *
 * A pool holds capacity rather than bookings, so it keeps the word resource in
 * its name. What it holds is described by its allocations, and what it will
 * accept by its policies. A pool also carries a priority and an enabled flag,
 * so the offer factory can ask enabled pools in the order an administrator
 * chose.
 *
 * A pool is not a lifecycle component. Whether it can serve a request depends
 * on who is asking and when, so that answer belongs to its policies rather than
 * to a phase.
 *
 */
public interface AbstractStorageResourcePool
extends Component
    {

    /**
     * The name of the volume pool a deployment gets when it configures nothing.
     *
     */
    public static final String DEFAULT_VOLUME_POOL_NAME = "default-volume" ;

    /**
     * The name of the bind pool a deployment gets when it configures nothing.
     *
     */
    public static final String DEFAULT_BIND_POOL_NAME = "default-bind" ;

    /**
     * Get the order in which this pool is offered.
     *
     * Lower values are offered first.
     *
     */
    public int getPriority();

    /**
     * Whether this pool can make offers.
     *
     */
    public boolean isEnabled();

    /**
     * Get the storage class this pool serves.
     *
     * The class belongs to the storage-specific interface, not to the generic
     * resource pool abstraction, because compute pools do not have one.
     *
     */
    public StorageClass getStorageClass();

    }
