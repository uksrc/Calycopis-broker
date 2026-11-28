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
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 2,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-11-27T18:30:00/2026-11-27T18:41:01",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 43,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.entities.storage.simple;

import java.net.URI;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.AbstractStorageReservationEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.AbstractStorageReservationValidator;
import net.ivoa.calycopis.broker.engine.util.URIBuilder;
import net.ivoa.calycopis.openapi.spring.model.IvoaAbstractStorageResource;
import net.ivoa.calycopis.openapi.spring.model.IvoaSimpleStorageResource;
import net.ivoa.calycopis.openapi.spring.model.IvoaSimpleStorageSize;

/**
 * A SimpleStorageReservation Entity.
 *
 */
@Entity
@Table(
    name = "simplestoragereservations"
    )
public abstract class SimpleStorageReservationEntity
extends AbstractStorageReservationEntity
implements SimpleStorageReservation
    {
    
    @Override
    public URI getKind()
        {
        return SimpleStorageReservation.KIND_DISCRIMINATOR;
        }

    /**
     * Protected constructor for JPA entities.
     *
     */
    protected SimpleStorageReservationEntity()
        {
        super();
        }
    
    /**
     * Protected constructor used by our Factories.
     *
     */
    protected SimpleStorageReservationEntity(
        final SimpleExecutionSessionEntity session,
        final AbstractStorageReservationValidator.Result result
        ){
        super(
            session,
            result
            );

        //
        // A validated SimpleStorageResource carries the requested size. The
        // Docker bind and volume templates are AbstractStorageResources and
        // carry no size, so those reservations default to the broker size.
        IvoaSimpleStorageSize requested = null;
        if (result.getObject() instanceof IvoaSimpleStorageResource simple)
            {
            requested = simple.getSize();
            }

        Long minsize = DEFAULT_SIZE_GIB;
        Long maxsize = DEFAULT_SIZE_GIB;
        if (requested != null)
            {
            if (requested.getMin() != null)
                {
                minsize = requested.getMin();
                }
            if (requested.getMax() != null)
                {
                maxsize = requested.getMax();
                }
            else {
                maxsize = minsize;
                }
            }
        if (maxsize < minsize)
            {
            maxsize = minsize;
            }

        //
        // Stage 2 has no pool offer. The validated size is the offer on the
        // direct path, so the offered columns mirror the requested values.
        this.minrequestedsize = minsize;
        this.maxrequestedsize = maxsize;
        this.minofferedsize   = minsize;
        this.maxofferedsize   = maxsize;
        }

    /**
     * The default storage size, in GiB, when a request supplies no size.
     *
     */
    public static final Long DEFAULT_SIZE_GIB = 1L;

    @Column(name="minrequestedsize")
    private Long minrequestedsize;
    @Override
    public Long getMinRequestedSize()
        {
        return this.minrequestedsize;
        }

    @Column(name="maxrequestedsize")
    private Long maxrequestedsize;
    @Override
    public Long getMaxRequestedSize()
        {
        return this.maxrequestedsize;
        }

    @Column(name="minofferedsize")
    private Long minofferedsize;
    @Override
    public Long getMinOfferedSize()
        {
        return this.minofferedsize;
        }

    @Column(name="maxofferedsize")
    private Long maxofferedsize;
    @Override
    public Long getMaxOfferedSize()
        {
        return this.maxofferedsize;
        }

    @Override
    public IvoaAbstractStorageResource makeBean(URIBuilder uribuilder)
        {
        return fillBean(
            new IvoaSimpleStorageResource().meta(
                this.makeMeta(
                    uribuilder
                    )
                )
            );
        }

    protected IvoaSimpleStorageResource fillBean(final IvoaSimpleStorageResource bean)
        {
        super.fillBean(bean);
        //
        // Add the storage properties.
        //
        IvoaSimpleStorageSize size = new IvoaSimpleStorageSize();
        size.setMin(this.minofferedsize);
        size.setMax(this.maxofferedsize);
        bean.setSize(size);
        return bean;
        }
    }

