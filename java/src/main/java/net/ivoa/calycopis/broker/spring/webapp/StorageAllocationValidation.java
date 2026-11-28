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

import net.ivoa.calycopis.broker.spring.webapp.error.BrokerProblemException;
import net.ivoa.calycopis.broker.spring.webapp.error.ProblemType;

/**
 * Validation shared by the storage allocation create and update endpoints.
 *
 * The entity enforces the same rules, but a value that is rejected here gets a
 * 400 with a problem body rather than an exception from deeper in the broker.
 *
 */
final class StorageAllocationValidation
    {

    /**
     * Private constructor, this is a utility class.
     *
     */
    private StorageAllocationValidation()
        {
        }

    /**
     * Require a positive size in GiB.
     *
     */
    static long size(final Long size)
        {
        if (size == null || size.longValue() <= 0L)
            {
            throw new BrokerProblemException(
                ProblemType.INVALID_STORAGE_ALLOCATION,
                "A storage allocation needs a positive size."
                );
            }
        return size.longValue();
        }

    /**
     * Use now when the request omits the start.
     *
     */
    static Instant start(final Instant start)
        {
        if (start == null)
            {
            return Instant.now();
            }
        return start;
        }

    /**
     * Check an optional end against the start.
     *
     */
    static Instant end(final Instant end, final Instant start)
        {
        if (end != null && !end.isAfter(start))
            {
            throw new BrokerProblemException(
                ProblemType.INVALID_STORAGE_ALLOCATION,
                "A storage allocation must end after it starts."
                );
            }
        return end;
        }

    }
