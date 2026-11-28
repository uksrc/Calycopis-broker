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

package net.ivoa.calycopis.broker.spring.pools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.ivoa.calycopis.broker.engine.entities.storage.pool.AbstractStorageResourcePool;
import net.ivoa.calycopis.broker.engine.entities.storage.pool.StorageClass;
import net.ivoa.calycopis.broker.engine.entities.storage.pool.simple.SimpleStorageResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.pool.simple.SimpleStorageResourcePoolEntityFactory;

/**
 * Tests for the storage pool startup seed.
 *
 * The factory is a stand-in that remembers what it holds by name, which is
 * enough to check the properties that matter: both defaults are always seeded,
 * a second run creates nothing, and a configured class is carried onto the
 * entity.
 *
 */
public class StoragePoolBootstrapTest
    {
    @Test
    public void testSeedCreatesBothDefaults() throws Exception
        {
        StubFactory pools = new StubFactory();
        StoragePoolBootstrap bootstrap = bootstrapOf(
            List.<StoragePoolSpec>of(),
            pools
            );

        bootstrap.run(null);

        Assertions.assertEquals(
            2,
            pools.size(),
            "A deployment with no storage configuration gets both defaults"
            );
        Assertions.assertEquals(
            StorageClass.VOLUME,
            pools.findByName(AbstractStorageResourcePool.DEFAULT_VOLUME_POOL_NAME).get().getStorageClass(),
            "default-volume must serve VOLUME"
            );
        Assertions.assertEquals(
            StorageClass.BIND,
            pools.findByName(AbstractStorageResourcePool.DEFAULT_BIND_POOL_NAME).get().getStorageClass(),
            "default-bind must serve BIND"
            );
        }

    @Test
    public void testSeedIsIdempotent() throws Exception
        {
        StubFactory pools = new StubFactory();
        StoragePoolBootstrap bootstrap = bootstrapOf(
            List.<StoragePoolSpec>of(),
            pools
            );

        bootstrap.run(null);
        bootstrap.run(null);

        Assertions.assertEquals(
            2,
            pools.size(),
            "A second run must not create a second pool per name"
            );
        }

    @Test
    public void testConfiguredPoolCarriesItsClass() throws Exception
        {
        StubFactory pools = new StubFactory();
        StoragePoolBootstrap bootstrap = bootstrapOf(
            List.of(
                spec("archive", "BIND", null, null)
                ),
            pools
            );

        bootstrap.run(null);

        Assertions.assertEquals(
            3,
            pools.size(),
            "A configured pool is seeded alongside both defaults"
            );
        Assertions.assertEquals(
            StorageClass.BIND,
            pools.findByName("archive").get().getStorageClass(),
            "A configured pool must carry its configured class"
            );
        }

    @Test
    public void testConfiguredDefaultKeepsItsPromisedClass() throws Exception
        {
        StubFactory pools = new StubFactory();
        StoragePoolBootstrap bootstrap = bootstrapOf(
            List.of(
                spec("default-volume", "BIND", 10, false)
                ),
            pools
            );

        bootstrap.run(null);

        SimpleStorageResourcePoolEntity volume = pools.findByName(
            AbstractStorageResourcePool.DEFAULT_VOLUME_POOL_NAME
            ).get();
        Assertions.assertEquals(
            StorageClass.VOLUME,
            volume.getStorageClass(),
            "default-volume must keep VOLUME even when the configuration says BIND"
            );
        Assertions.assertEquals(
            10,
            volume.getPriority(),
            "A configured default may change its priority"
            );
        Assertions.assertFalse(
            volume.isEnabled(),
            "A configured default may change its enabled flag"
            );
        }

    @Test
    public void testConfiguredDefaultWithoutDescriptionKeepsTheDefaultText() throws Exception
        {
        StubFactory pools = new StubFactory();
        StoragePoolSpec configured = spec("default-bind", null, null, null);
        configured.setDescription(null);
        StoragePoolBootstrap bootstrap = bootstrapOf(
            List.of(configured),
            pools
            );

        bootstrap.run(null);

        Assertions.assertEquals(
            StoragePoolBootstrap.DEFAULT_BIND_DESCRIPTION,
            pools.findByName(AbstractStorageResourcePool.DEFAULT_BIND_POOL_NAME).get().getDescription(),
            "A configured default without a description keeps the built-in text"
            );
        }

    @Test
    public void testConfiguredPoolWithoutClassDefaultsToVolume() throws Exception
        {
        StubFactory pools = new StubFactory();
        StoragePoolBootstrap bootstrap = bootstrapOf(
            List.of(
                spec("archive", null, null, null)
                ),
            pools
            );

        bootstrap.run(null);

        Assertions.assertEquals(
            StorageClass.VOLUME,
            pools.findByName("archive").get().getStorageClass(),
            "A configured pool with no class defaults to VOLUME"
            );
        }

    @Test
    public void testUnknownConfiguredClassIsSkipped() throws Exception
        {
        StubFactory pools = new StubFactory();
        StoragePoolBootstrap bootstrap = bootstrapOf(
            List.of(
                spec("archive", "TAPE", null, null)
                ),
            pools
            );

        bootstrap.run(null);

        Assertions.assertEquals(
            2,
            pools.size(),
            "An invalid configured class must not create a pool"
            );
        Assertions.assertTrue(
            pools.findByName("archive").isEmpty(),
            "An invalid configured class must skip the configured pool"
            );
        }

    @Test
    public void testDuplicateConfiguredNameIsSeededOnce() throws Exception
        {
        StubFactory pools = new StubFactory();
        StoragePoolBootstrap bootstrap = bootstrapOf(
            List.of(
                spec("archive", "VOLUME", null, null),
                spec("archive", "BIND", null, null)
                ),
            pools
            );

        bootstrap.run(null);

        Assertions.assertEquals(
            3,
            pools.size(),
            "A duplicate configured name must not create two pools"
            );
        Assertions.assertEquals(
            StorageClass.VOLUME,
            pools.findByName("archive").get().getStorageClass(),
            "The first declaration must win the duplicate name"
            );
        }

    @Test
    public void testNegativeConfiguredPriorityBecomesZero() throws Exception
        {
        StubFactory pools = new StubFactory();
        StoragePoolBootstrap bootstrap = bootstrapOf(
            List.of(
                spec("archive", "VOLUME", -1, null)
                ),
            pools
            );

        bootstrap.run(null);

        Assertions.assertEquals(
            0,
            pools.findByName("archive").get().getPriority(),
            "A negative configured priority must become zero"
            );
        }

    /**
     * Build a bootstrap around a settings bean and a stub factory.
     *
     */
    private static StoragePoolBootstrap bootstrapOf(
        final List<StoragePoolSpec> specs,
        final StubFactory pools
        ){
        PoolsSettingsImpl settings = new PoolsSettingsImpl();
        settings.setStorage(
            specs
            );
        return new StoragePoolBootstrap(
            settings,
            pools
            );
        }

    /**
     * A storage pool spec with the values the tests vary.
     *
     */
    private static StoragePoolSpec spec(
        final String name,
        final String storageClass,
        final Integer priority,
        final Boolean enabled
        ){
        StoragePoolSpec spec = new StoragePoolSpec();
        spec.setName(
            name
            );
        spec.setDescription(
            "Test pool " + name + "."
            );
        spec.setStorageClass(
            storageClass
            );
        spec.setPriority(
            priority
            );
        spec.setEnabled(
            enabled
            );
        return spec;
        }

    /**
     * A factory stand-in that keeps pools by name.
     *
     */
    private static class StubFactory
    implements SimpleStorageResourcePoolEntityFactory
        {
        private final Map<String, SimpleStorageResourcePoolEntity> pools = new LinkedHashMap<>();

        @Override
        public Optional<SimpleStorageResourcePoolEntity> select(final UUID uuid)
            {
            for (SimpleStorageResourcePoolEntity entity : this.pools.values())
                {
                if (uuid != null && uuid.equals(entity.getUuid()))
                    {
                    return Optional.of(entity);
                    }
                }
            return Optional.empty();
            }

        @Override
        public Optional<SimpleStorageResourcePoolEntity> findByName(final String name)
            {
            return Optional.ofNullable(
                this.pools.get(name)
                );
            }

        @Override
        public List<SimpleStorageResourcePoolEntity> list()
            {
            return new ArrayList<>(
                this.pools.values()
                );
            }

        @Override
        public SimpleStorageResourcePoolEntity create(
            final String name,
            final String description,
            final StorageClass storageClass
            ){
            return this.create(
                name,
                description,
                0,
                true,
                storageClass
                );
            }

        @Override
        public SimpleStorageResourcePoolEntity create(
            final String name,
            final String description,
            final int priority,
            final boolean enabled,
            final StorageClass storageClass
            ){
            SimpleStorageResourcePoolEntity entity = new SimpleStorageResourcePoolEntity(
                name,
                description,
                priority,
                enabled,
                storageClass
                );
            this.pools.put(
                name,
                entity
                );
            return entity;
            }

        public int size()
            {
            return this.pools.size();
            }

        }

    }
