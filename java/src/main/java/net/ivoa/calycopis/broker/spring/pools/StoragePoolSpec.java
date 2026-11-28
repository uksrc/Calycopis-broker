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
 *     },
 *     {
 *     "interval": "2026-11-28T04:50:00/2026-11-28T05:40:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 10,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.spring.pools;

import java.util.List;

/**
 * One storage pool as it is written in pools.yaml.
 *
 * The bootstrap turns these into entities. The class is a String rather than an
 * enum so that a missing value can mean the default and an unknown value can be
 * reported and skipped without failing the whole configuration binding.
 *
 */
public class StoragePoolSpec
    {
    private String name;
    private String description;
    private Integer priority;
    private Boolean enabled;
    private String storageClass;
    private List<StorageAllocationSpec> allocations;

    public String getName()
        {
        return this.name;
        }

    public void setName(final String name)
        {
        this.name = name;
        }

    public String getDescription()
        {
        return this.description;
        }

    public void setDescription(final String description)
        {
        this.description = description;
        }

    public Integer getPriority()
        {
        return this.priority;
        }

    public void setPriority(final Integer priority)
        {
        this.priority = priority;
        }

    public Boolean getEnabled()
        {
        return this.enabled;
        }

    public void setEnabled(final Boolean enabled)
        {
        this.enabled = enabled;
        }

    public String getStorageClass()
        {
        return this.storageClass;
        }

    public void setStorageClass(final String storageClass)
        {
        this.storageClass = storageClass;
        }

    public List<StorageAllocationSpec> getAllocations()
        {
        return this.allocations;
        }

    public void setAllocations(final List<StorageAllocationSpec> allocations)
        {
        this.allocations = allocations;
        }

    }
