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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import net.ivoa.calycopis.broker.engine.entities.storage.pool.AbstractStorageResourcePool;
import net.ivoa.calycopis.broker.engine.entities.storage.pool.StorageClass;
import net.ivoa.calycopis.broker.engine.entities.storage.pool.simple.SimpleStorageResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.pool.simple.SimpleStorageResourcePoolEntityFactory;

/**
 * Creates the storage pools a deployment declares, once, at startup.
 *
 * The two default pools are always seeded, whether or not pools.yaml names
 * them. A configured default may change its description, priority and enabled
 * flag, but its class is forced to the class its name promises.
 *
 * The seed is idempotent by name: a pool is looked up before it is created, so
 * a second run against the same database finds it and leaves it alone. A bad
 * configured class is logged and skipped rather than stopping the broker; the
 * defaults are still seeded.
 *
 */
@Slf4j
@Component
public class StoragePoolBootstrap
implements ApplicationRunner
    {
    /**
     * The description of the volume pool the bootstrap always supplies.
     *
     */
    public static final String DEFAULT_VOLUME_DESCRIPTION = "The default volume storage pool." ;

    /**
     * The description of the bind pool the bootstrap always supplies.
     *
     */
    public static final String DEFAULT_BIND_DESCRIPTION = "The default bind storage pool." ;

    private final PoolsSettings settings;
    private final SimpleStorageResourcePoolEntityFactory poolFactory;

    public StoragePoolBootstrap(
        final PoolsSettings settings,
        final SimpleStorageResourcePoolEntityFactory poolFactory
        ){
        this.settings = settings;
        this.poolFactory = poolFactory;
        }

    @Override
    public void run(final ApplicationArguments args) throws Exception
        {
        List<StoragePoolSpec> specs = resolve(
            this.settings.getStorage()
            );
        log.info(
            "Seeding [{}] storage pool(s)",
            specs.size()
            );
        for (StoragePoolSpec spec : specs)
            {
            this.seed(
                spec
                );
            }
        }

    /**
     * The pools to seed: the two defaults, then what the configuration declares.
     *
     * The defaults are inserted first and keyed by name, so a configured default
     * can refine one without creating a second row.
     *
     */
    static List<StoragePoolSpec> resolve(final List<StoragePoolSpec> configured)
        {
        Map<String, StoragePoolSpec> resolved = new LinkedHashMap<>();
        Set<String> declaredNames = new HashSet<>();
        resolved.put(
            AbstractStorageResourcePool.DEFAULT_VOLUME_POOL_NAME,
            defaultSpec(
                AbstractStorageResourcePool.DEFAULT_VOLUME_POOL_NAME,
                DEFAULT_VOLUME_DESCRIPTION,
                StorageClass.VOLUME
                )
            );
        resolved.put(
            AbstractStorageResourcePool.DEFAULT_BIND_POOL_NAME,
            defaultSpec(
                AbstractStorageResourcePool.DEFAULT_BIND_POOL_NAME,
                DEFAULT_BIND_DESCRIPTION,
                StorageClass.BIND
                )
            );

        if (configured == null)
            {
            return new ArrayList<StoragePoolSpec>(
                resolved.values()
                );
            }

        for (StoragePoolSpec spec : configured)
            {
            if (spec == null || spec.getName() == null || spec.getName().isBlank())
                {
                log.error(
                    "Ignoring a storage pool with no name"
                    );
                continue;
                }
            String name = spec.getName().trim();
            boolean defaultName = isDefaultName(
                name
                );
            if (defaultName)
                {
                if (!declaredNames.add(name))
                    {
                    log.warn(
                        "Duplicate configured storage pool [{}]; keeping the first declaration",
                        name
                        );
                    continue;
                    }
                StorageClass defaultClass = defaultClassFor(
                    name
                    );
                String configuredClass = spec.getStorageClass();
                if (configuredClass != null && !configuredClass.isBlank())
                    {
                    Optional<StorageClass> parsed = StorageClass.parse(
                        configuredClass
                        );
                    if (parsed.isEmpty())
                        {
                        log.warn(
                            "Ignoring unknown storage class [{}] for default pool [{}]; keeping [{}]",
                            configuredClass,
                            name,
                            defaultClass
                            );
                        }
                    else if (parsed.get() != defaultClass)
                        {
                        log.warn(
                            "Ignoring storage class [{}] for default pool [{}]; keeping [{}]",
                            parsed.get(),
                            name,
                            defaultClass
                            );
                        }
                    }
                StoragePoolSpec merged = copy(
                    spec,
                    name,
                    defaultClass
                    );
                if (merged.getDescription() == null)
                    {
                    if (defaultClass == StorageClass.VOLUME)
                        {
                        merged.setDescription(
                            DEFAULT_VOLUME_DESCRIPTION
                            );
                        }
                    else {
                        merged.setDescription(
                            DEFAULT_BIND_DESCRIPTION
                            );
                        }
                    }
                resolved.put(name, merged);
                }
            else {
                if (!declaredNames.add(name))
                    {
                    log.warn(
                        "Duplicate configured storage pool [{}]; keeping the first declaration",
                        name
                        );
                    continue;
                    }
                String configuredClass = spec.getStorageClass();
                StorageClass storageClass = StorageClass.DEFAULT;
                if (configuredClass != null && !configuredClass.isBlank())
                    {
                    Optional<StorageClass> parsed = StorageClass.parse(
                        configuredClass
                        );
                    if (parsed.isEmpty())
                        {
                        log.error(
                            "Ignoring storage pool [{}] with unknown storage class [{}]",
                            name,
                            configuredClass
                            );
                        continue;
                        }
                    storageClass = parsed.get();
                    }
                resolved.put(
                    name,
                    copy(
                        spec,
                        name,
                        storageClass
                        )
                    );
                }
            }

        return new ArrayList<StoragePoolSpec>(
            resolved.values()
            );
        }

    /**
     * Seed one spec, unless a pool with its name already exists.
     *
     */
    private void seed(final StoragePoolSpec spec)
        {
        Optional<SimpleStorageResourcePoolEntity> existing = this.poolFactory.findByName(
            spec.getName()
            );
        if (existing.isPresent())
            {
            log.info(
                "Storage pool [{}] already exists",
                spec.getName()
                );
            return;
            }

        int priority = 0;
        if (spec.getPriority() != null)
            {
            if (spec.getPriority().intValue() < 0)
                {
                log.warn(
                    "Ignoring a negative priority for storage pool [{}]",
                    spec.getName()
                    );
                }
            else {
                priority = spec.getPriority().intValue();
                }
            }

        boolean enabled = true;
        if (spec.getEnabled() != null)
            {
            enabled = spec.getEnabled().booleanValue();
            }

        SimpleStorageResourcePoolEntity entity = this.poolFactory.create(
            spec.getName(),
            spec.getDescription(),
            priority,
            enabled,
            specClass(
                spec
                )
            );
        log.info(
            "Created storage pool [{}] [{}] with class [{}], priority [{}] and enabled [{}]",
            entity.getName(),
            entity.getUuid(),
            entity.getStorageClass(),
            priority,
            enabled
            );
        }

    /**
     * The class a resolved spec carries, defaulting only if it is absent.
     *
     */
    private static StorageClass specClass(final StoragePoolSpec spec)
        {
        Optional<StorageClass> parsed = StorageClass.parse(
            spec.getStorageClass()
            );
        if (parsed.isPresent())
            {
            return parsed.get();
            }
        return StorageClass.DEFAULT;
        }

    /**
     * Make a copy of a configured spec with its resolved name and class.
     *
     */
    private static StoragePoolSpec copy(
        final StoragePoolSpec source,
        final String name,
        final StorageClass storageClass
        ){
        StoragePoolSpec target = new StoragePoolSpec();
        target.setName(
            name
            );
        if (source.getDescription() != null)
            {
            target.setDescription(
                source.getDescription()
                );
            }
        target.setPriority(
            source.getPriority()
            );
        target.setEnabled(
            source.getEnabled()
            );
        target.setStorageClass(
            storageClass.name()
            );
        return target;
        }

    /**
     * A default pool spec.
     *
     */
    static StoragePoolSpec defaultSpec(
        final String name,
        final String description,
        final StorageClass storageClass
        ){
        StoragePoolSpec spec = new StoragePoolSpec();
        spec.setName(
            name
            );
        spec.setDescription(
            description
            );
        spec.setPriority(
            0
            );
        spec.setEnabled(
            true
            );
        spec.setStorageClass(
            storageClass.name()
            );
        return spec;
        }

    /**
     * Whether a name is one of the two defaults.
     *
     */
    static boolean isDefaultName(final String name)
        {
        if (name == null)
            {
            return false;
            }
        String trimmed = name.trim();
        return trimmed.equals(
            AbstractStorageResourcePool.DEFAULT_VOLUME_POOL_NAME
            ) || trimmed.equals(
            AbstractStorageResourcePool.DEFAULT_BIND_POOL_NAME
            );
        }

    /**
     * The class a default name promises.
     *
     */
    static StorageClass defaultClassFor(final String name)
        {
        if (name != null && name.trim().equals(
            AbstractStorageResourcePool.DEFAULT_BIND_POOL_NAME
            ))
            {
            return StorageClass.BIND;
            }
        return StorageClass.VOLUME;
        }

    }
