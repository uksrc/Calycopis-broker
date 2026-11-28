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

import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the storage class vocabulary.
 *
 */
public class StorageClassTest
    {
    @Test
    public void testParsesVolume()
        {
        Assertions.assertEquals(
            StorageClass.VOLUME,
            StorageClass.parse("VOLUME").get()
            );
        }

    @Test
    public void testParsesBind()
        {
        Assertions.assertEquals(
            StorageClass.BIND,
            StorageClass.parse("BIND").get()
            );
        }

    @Test
    public void testParsesCaseInsensitively()
        {
        Assertions.assertEquals(
            StorageClass.VOLUME,
            StorageClass.parse(" volume ").get()
            );
        Assertions.assertEquals(
            StorageClass.BIND,
            StorageClass.parse("bind").get()
            );
        }

    @Test
    public void testRejectsMissingAndUnknownValues()
        {
        Assertions.assertEquals(
            Optional.empty(),
            StorageClass.parse(null)
            );
        Assertions.assertEquals(
            Optional.empty(),
            StorageClass.parse("")
            );
        Assertions.assertEquals(
            Optional.empty(),
            StorageClass.parse("   ")
            );
        Assertions.assertEquals(
            Optional.empty(),
            StorageClass.parse("TAPE")
            );
        }

    @Test
    public void testDefaultIsVolume()
        {
        Assertions.assertEquals(
            StorageClass.VOLUME,
            StorageClass.DEFAULT
            );
        }

    }
