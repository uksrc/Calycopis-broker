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
 *     "timestamp": "2026-04-14T17:00:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 5,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-06-03T01:33:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 5,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-06-23T14:03:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 5,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-11-16T05:14:00/2026-11-16T05:40:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 10,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-11-27T18:30:00/2026-11-27T18:41:01",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 14,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.entities.storage;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.component.LifecycleComponentEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.AbstractComputeReservationEntity;
import net.ivoa.calycopis.broker.engine.entities.data.AbstractDataResourceEntity;
import net.ivoa.calycopis.broker.engine.entities.data.AbstractDataResourceValidator;
import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.entities.volume.AbstractVolumeMountEntity;
import net.ivoa.calycopis.broker.engine.util.ListWrapper;
import net.ivoa.calycopis.broker.engine.util.URIBuilder;
import net.ivoa.calycopis.openapi.spring.model.IvoaAbstractStorageResource;
import net.ivoa.calycopis.openapi.spring.model.IvoaLifecyclePhase;

/**
 * 
 */
@Slf4j
@Entity
@Table(
    name = "abstractstoragereservations"
    )
@Inheritance(
    strategy = InheritanceType.JOINED
    )
public abstract class AbstractStorageReservationEntity
extends LifecycleComponentEntity
implements AbstractStorageReservation
    {
    
    /**
     * Protected constructor for JPA entities.
     * 
     */
    protected AbstractStorageReservationEntity()
        {
        super();
        }

    /**
     * Protected constructor used by derived classes.
     * 
     */
    protected AbstractStorageReservationEntity(
        final SimpleExecutionSessionEntity session,
        final AbstractStorageReservationValidator.Result result
        ){
        super(
            result.getMeta(),
            session.getOwner()
            );

        this.session = session;
        this.session.addStorageReservation(
            this
            );

        //
        // A storage reservation holds the compute candidate's window. The
        // parser builds compute before storage, and the compute reservation
        // already carries the offer-derived absolute schedule.
        AbstractComputeReservationEntity compute = session.getComputeReservation();
        if (compute == null)
            {
            throw new IllegalStateException(
                "A storage reservation requires a compute reservation"
                );
            }

        //
        // The latest data resource must finish preparing when compute starts
        // preparing, so storage has to be available before every data
        // preparation. The data entities are built after storage, but the
        // validator result carries their prepare durations.
        long dataPrepareSeconds = maxDataPrepareDuration(result);
        long storageAvailableStartSeconds = compute.getPrepareStartInstantSeconds() - dataPrepareSeconds;
        long storageAvailableEndSeconds = compute.getAvailableStartInstantSeconds() + compute.getAvailableDurationSeconds();

        //
        // Storage prepares before it becomes available, and stays available
        // through data download and compute execution.
        this.prepareDurationSeconds     = result.getPrepareDuration();
        this.prepareStartInstantSeconds = storageAvailableStartSeconds - this.prepareDurationSeconds;
        this.availableStartInstantSeconds  = storageAvailableStartSeconds;
        this.availableStartDurationSeconds = 0L;
        this.availableDurationSeconds      = storageAvailableEndSeconds - storageAvailableStartSeconds;

        //
        // Release after the compute release ends. The lifecycle rules still
        // wait for the dependent compute and data resources to be RELEASED.
        this.releaseDurationSeconds     = result.getReleaseDuration();
        this.releaseStartInstantSeconds = compute.getReleaseStartInstantSeconds() + compute.getReleaseDurationSeconds();

        }

    /**
     * The largest prepare duration among the data resources this storage serves.
     *
     */
    private static long maxDataPrepareDuration(final AbstractStorageReservationValidator.Result result)
        {
        long max = 0L;
        for (AbstractDataResourceValidator.Result dataResult : result.getDataResourceResults())
            {
            Long duration = dataResult.getPrepareDuration();
            if ((duration != null) && (duration > max))
                {
                max = duration;
                }
            }
        return max;
        }

    @JoinColumn(name = "session", referencedColumnName = "uuid", nullable = false)
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private SimpleExecutionSessionEntity session;
    @Override
    public SimpleExecutionSessionEntity getSession()
        {
        return this.session;
        }

    @OneToMany(
        mappedBy = "storage",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL,
        orphanRemoval = true
        )
    List<AbstractDataResourceEntity> dataresources = new ArrayList<AbstractDataResourceEntity>();

    @Override
    public List<AbstractDataResourceEntity> getDataResources()
        {
        return dataresources;
        }

    public void addDataResource(final AbstractDataResourceEntity dataResource)
        {
        dataResource.setStorage(
            this
            );
        dataresources.add(
            dataResource
            );
        }

    @OneToMany(
        mappedBy = "storageResource",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL,
        orphanRemoval = true
        )
    List<AbstractVolumeMountEntity> volumeMounts = new ArrayList<AbstractVolumeMountEntity>();

    @Override
    public List<AbstractVolumeMountEntity> getVolumeMounts()
        {
        return volumeMounts;
        }

    public void addVolumeMount(final AbstractVolumeMountEntity volume)
        {
        volume.setStorageReservation(
            this
            );
        volumeMounts.add(
            volume
            );
        }
    
    protected IvoaLifecyclePhase checkReleaseActionRules()
        {
        //
        // Storage resources MUST wait until the compute resources linked to them have been RELEASED.
        for (AbstractVolumeMountEntity volumeMount : this.getVolumeMounts())
            {
            AbstractComputeReservationEntity computeResource = volumeMount.getComputeReservation();
            if (computeResource == null)
                {
                log.error(
                    "Storage resource [{}][{}] volume mount [{}][{}] has no compute resource reference",
                    this.getUuid(),
                    this.getClass().getSimpleName(),
                    volumeMount.getUuid(),
                    volumeMount.getClass().getSimpleName()
                    );
                return IvoaLifecyclePhase.FAILED;
                }
            else if (computeResource.getPhase().compareTo(IvoaLifecyclePhase.RELEASING) <= 0)
                {
                log.debug(
                    "Storage resource [{}][{}] waiting for compute resource [{}][{}][{}] to be RELEASED",
                    this.getUuid(),
                    this.getClass().getSimpleName(),
                    computeResource.getUuid(),
                    computeResource.getClass().getSimpleName(),
                    computeResource.getPhase()
                    );
                return IvoaLifecyclePhase.WAITING;
                }
            }
        //
        // Storage resources MUST wait until their data resources have been released.
        for (AbstractDataResourceEntity dataResource : this.getDataResources())
            {
            if (dataResource.getPhase().compareTo(IvoaLifecyclePhase.RELEASING) <= 0)
                {
                log.debug(
                    "Storage resource [{}][{}] waiting for data resource [{}][{}][{}] to be RELEASED",
                    this.getUuid(),
                    this.getClass().getSimpleName(),
                    dataResource.getUuid(),
                    dataResource.getClass().getSimpleName(),
                    dataResource.getPhase()
                    );
                return IvoaLifecyclePhase.WAITING;
                }
            }
        return IvoaLifecyclePhase.RELEASING;
        }

    public abstract IvoaAbstractStorageResource makeBean(final URIBuilder builder);
    
    protected IvoaAbstractStorageResource fillBean(final IvoaAbstractStorageResource bean)
        {
        bean.setKind(
            this.getKind()
            );
        bean.setPhase(
            this.getPhase()
            );
        bean.setSchedule(
            this.makeScheduleBean()
            );
        bean.setData(
            new ListWrapper<String, AbstractDataResourceEntity>(
                dataresources
                ){
                public String wrap(final AbstractDataResourceEntity inner)
                    {
                    return inner.getUuid().toString();
                    }
                }
            );
        bean.setCosts(
            this.getCostBeans()
            );
        bean.setMetrics(
            this.getMetricBeans()
            );
        return bean;
        }

    @Override
    public void link(AbstractStorageLinker linker)
        {
        }

    @Override
    protected URI getWebappPath()
        {
        return AbstractStorageReservation.WEBAPP_PATH;
        }

    }
