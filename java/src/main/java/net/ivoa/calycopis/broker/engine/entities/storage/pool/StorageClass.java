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

import java.util.Locale;
import java.util.Optional;

/**
 * The storage implementation a pool serves.
 *
 * The class is an admin and configuration field, not an OpenAPI field. Explicit
 * storage and storage for http(s) data are volume-mounted; auto-created storage
 * for file:// data is bind-mounted. The mock platform's simple storage uses the
 * volume class because it is broker-managed rather than host-path-backed.
 *
 */
public enum StorageClass
    {
    VOLUME,
    BIND;

    /**
     * The class an admin create gets when it says nothing.
     *
     */
    public static final StorageClass DEFAULT = VOLUME;

    /**
     * Parse a configured or requested class.
     *
     * A null or blank value is not a class. The caller decides whether blank
     * means the default; an unknown value is an error.
     *
     */
    public static Optional<StorageClass> parse(final String value)
        {
        if (value == null || value.isBlank())
            {
            return Optional.empty();
            }
        try {
            return Optional.of(
                StorageClass.valueOf(
                    value.trim().toUpperCase(Locale.ROOT)
                    )
                );
            }
        catch (IllegalArgumentException exception)
            {
            return Optional.empty();
            }
        }

    }
