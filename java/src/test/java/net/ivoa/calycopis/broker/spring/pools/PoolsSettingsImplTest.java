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
 *     "interval": "2026-11-16T07:05:00/2026-11-16T08:20:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-11-17T15:55:00/2026-11-17T18:30:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 20,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-11-28T03:00:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 10,
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

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import net.ivoa.calycopis.broker.engine.entities.compute.pool.AbstractComputeResourcePool;

/**
 * Tests for the pools settings and the compute pool the bootstrap falls back to.
 *
 * The binding tests cover the path from the property names in pools.yaml to the
 * settings bean, for both compute and storage. The resolve tests cover the
 * compute rule that matters when a deployment says nothing: one pool, called
 * default.
 *
 */
public class PoolsSettingsImplTest
    {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(
            PoolsSettingsConfiguration.class
            );

    /**
     * With no configuration the bean must report no pools, because an empty
     * list is what makes the bootstrap supply the default.
     *
     */
    @Test
    public void testDefaultsToNoConfiguredPools()
        {
        contextRunner.run(
            context -> {
                PoolsSettings settings = context.getBean(
                    PoolsSettings.class
                    );
                Assertions.assertNotNull(
                    settings.getCompute(),
                    "The configured pools must not be null"
                    );
                Assertions.assertTrue(
                    settings.getCompute().isEmpty(),
                    "The configured pools must be empty by default"
                    );
                }
            );
        }

    /**
     * A configured pool must reach the bean with its name and description.
     *
     */
    @Test
    public void testBindsConfiguredPools()
        {
        contextRunner
            .withPropertyValues(
                "calycopis.broker.pools.compute[0].name=alpha",
                "calycopis.broker.pools.compute[0].description=The alpha pool."
                )
            .run(
                context -> {
                    PoolsSettings settings = context.getBean(
                        PoolsSettings.class
                        );
                    Assertions.assertEquals(
                        1,
                        settings.getCompute().size(),
                        "One configured pool must reach the settings bean"
                        );
                    ComputePoolSpec spec = settings.getCompute().get(0);
                    Assertions.assertEquals(
                        "alpha",
                        spec.getName(),
                        "The configured pool name must reach the settings bean"
                        );
                    Assertions.assertEquals(
                        "The alpha pool.",
                        spec.getDescription(),
                        "The configured pool description must reach the settings bean"
                        );
                    }
                );
        }

    /**
     * No configuration means one pool, and that pool is the default.
     *
     */
    @Test
    public void testResolveSuppliesTheDefaultPool()
        {
        for (List<ComputePoolSpec> configured : Arrays.asList(
            null,
            List.<ComputePoolSpec>of()
            ))
            {
            List<ComputePoolSpec> resolved = ComputePoolBootstrap.resolve(
                configured
                );
            Assertions.assertEquals(
                1,
                resolved.size(),
                "An empty configuration must resolve to one pool"
                );
            Assertions.assertEquals(
                AbstractComputeResourcePool.DEFAULT_POOL_NAME,
                resolved.get(0).getName(),
                "The fallback pool must be the default pool"
                );
            }
        }

    /**
     * A configured list is kept, and the default pool is added to it.
     *
     */
    @Test
    public void testResolveAddsTheDefaultPool()
        {
        ComputePoolSpec spec = new ComputePoolSpec();
        spec.setName("alpha");
        spec.setDescription("The alpha pool.");
        List<ComputePoolSpec> configured = List.of(spec);

        List<ComputePoolSpec> resolved = ComputePoolBootstrap.resolve(
            configured
            );
        Assertions.assertEquals(
            2,
            resolved.size(),
            "A configured list must gain the default pool"
            );
        Assertions.assertSame(
            spec,
            resolved.get(0),
            "The configured pool must come first"
            );
        Assertions.assertEquals(
            AbstractComputeResourcePool.DEFAULT_POOL_NAME,
            resolved.get(1).getName(),
            "The default pool must be added after the configured pools"
            );
        }

    /**
     * A configured list that already names the default pool does not get a
     * second one.
     *
     */
    @Test
    public void testResolveDoesNotDuplicateTheDefaultPool()
        {
        ComputePoolSpec spec = new ComputePoolSpec();
        spec.setName(AbstractComputeResourcePool.DEFAULT_POOL_NAME);

        List<ComputePoolSpec> resolved = ComputePoolBootstrap.resolve(
            List.of(spec)
            );
        Assertions.assertEquals(
            1,
            resolved.size(),
            "A declared default pool must not be duplicated"
            );
        Assertions.assertSame(
            spec,
            resolved.get(0),
            "The declared default pool must be the one returned"
            );
        }

    /**
     * With no configuration the storage list must be empty too.
     *
     */
    @Test
    public void testDefaultsToNoConfiguredStoragePools()
        {
        contextRunner.run(
            context -> {
                PoolsSettings settings = context.getBean(
                    PoolsSettings.class
                    );
                Assertions.assertNotNull(
                    settings.getStorage(),
                    "The configured storage pools must not be null"
                    );
                Assertions.assertTrue(
                    settings.getStorage().isEmpty(),
                    "The configured storage pools must be empty by default"
                    );
                }
            );
        }

    /**
     * A configured storage pool must reach the bean with its class and settings.
     *
     */
    @Test
    public void testBindsConfiguredStoragePools()
        {
        contextRunner
            .withPropertyValues(
                "calycopis.broker.pools.storage[0].name=archive",
                "calycopis.broker.pools.storage[0].description=The archive pool.",
                "calycopis.broker.pools.storage[0].storageClass=BIND",
                "calycopis.broker.pools.storage[0].priority=10",
                "calycopis.broker.pools.storage[0].enabled=false"
                )
            .run(
                context -> {
                    PoolsSettings settings = context.getBean(
                        PoolsSettings.class
                        );
                    Assertions.assertEquals(
                        1,
                        settings.getStorage().size(),
                        "One configured storage pool must reach the settings bean"
                        );
                    StoragePoolSpec spec = settings.getStorage().get(0);
                    Assertions.assertEquals(
                        "archive",
                        spec.getName(),
                        "The configured pool name must reach the settings bean"
                        );
                    Assertions.assertEquals(
                        "The archive pool.",
                        spec.getDescription(),
                        "The configured pool description must reach the settings bean"
                        );
                    Assertions.assertEquals(
                        "BIND",
                        spec.getStorageClass(),
                        "The configured storage class must reach the settings bean"
                        );
                    Assertions.assertEquals(
                        10,
                        spec.getPriority().intValue(),
                        "The configured priority must reach the settings bean"
                        );
                    Assertions.assertFalse(
                        spec.getEnabled().booleanValue(),
                        "The configured enabled flag must reach the settings bean"
                        );
                }
            );
        }

    /**
     * A configured storage allocation must reach the settings bean.
     *
     */
    @Test
    public void testBindsConfiguredStorageAllocation()
        {
        contextRunner
            .withPropertyValues(
                "calycopis.broker.pools.storage[0].name=archive",
                "calycopis.broker.pools.storage[0].allocations[0].size=512",
                "calycopis.broker.pools.storage[0].allocations[0].start=2026-12-01T00:00:00Z",
                "calycopis.broker.pools.storage[0].allocations[0].end=2026-12-02T00:00:00Z"
                )
            .run(
                context -> {
                    PoolsSettings settings = context.getBean(
                        PoolsSettings.class
                        );
                    StoragePoolSpec spec = settings.getStorage().get(0);
                    Assertions.assertNotNull(
                        spec.getAllocations(),
                        "The configured storage allocations must not be null"
                        );
                    Assertions.assertEquals(
                        1,
                        spec.getAllocations().size(),
                        "One configured storage allocation must reach the settings bean"
                        );
                    StorageAllocationSpec allocation = spec.getAllocations().get(0);
                    Assertions.assertEquals(
                        512L,
                        allocation.getSize().longValue(),
                        "The configured allocation size must reach the settings bean"
                        );
                    Assertions.assertEquals(
                        Instant.parse("2026-12-01T00:00:00Z"),
                        allocation.getStart(),
                        "The configured allocation start must reach the settings bean"
                        );
                    Assertions.assertEquals(
                        Instant.parse("2026-12-02T00:00:00Z"),
                        allocation.getEnd(),
                        "The configured allocation end must reach the settings bean"
                        );
                    }
                );
        }

    /**
     * Register the settings bean for the binding tests.
     *
     */
    @Configuration
    @EnableConfigurationProperties(PoolsSettingsImpl.class)
    static class PoolsSettingsConfiguration
        {
        }
    }
