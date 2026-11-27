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
 *       "value": 10,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-02-17T13:20:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 4,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-10-16T07:20:00/2026-10-16T08:40:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */
package net.ivoa.calycopis.broker.engine.entities.storage.simple;

import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContext;
import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.AbstractStorageReservationEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.AbstractStorageReservationEntityFactory;
import net.ivoa.calycopis.broker.engine.entities.storage.AbstractStorageReservationValidatorImpl;
import net.ivoa.calycopis.broker.engine.functional.validator.Validator;
import net.ivoa.calycopis.broker.engine.functional.validator.ValidatorTools;
import net.ivoa.calycopis.openapi.spring.model.IvoaAbstractStorageResource;
import net.ivoa.calycopis.openapi.spring.model.IvoaSimpleStorageResource;

/**
 * A Validator implementation to handle SimpleStorageReservations.
 * 
 */
@Slf4j
public abstract class SimpleStorageReservationValidatorImpl
extends AbstractStorageReservationValidatorImpl
implements SimpleStorageReservationValidator
    {

    /**
     * Factory for creating Entities.
     *
     */
    final AbstractStorageReservationEntityFactory entityFactory;

    /**
     * Protected constructor.
     * 
     */
    protected SimpleStorageReservationValidatorImpl(
        final AbstractStorageReservationEntityFactory entityFactory
        ){
        super();
        this.entityFactory = entityFactory;
        }
    
    @Override
    public SimpleStorageReservationValidator.Result validateObject(
        final IvoaAbstractStorageResource requested,
        final OfferSetRequestParserContext context
        ){
        log.debug("validate(IvoaAbstractStorageResource)");
        log.debug("Resource [{}][{}]", requested.getMeta(), requested.getClass().getName());
        switch(requested)
            {
            case IvoaSimpleStorageResource simple:
                return validate(
                    simple,
                    context
                    );
            default:
                return new SimpleStorageReservationValidator.ResultBean(
                    ResultEnum.CONTINUE
                    );
            }
        }

    /**
     * Validate an IvoaSimpleStorageResource.
     *
     */
    public SimpleStorageReservationValidator.Result validate(
        final IvoaSimpleStorageResource requested,
        final OfferSetRequestParserContext context
        ){
        log.debug("validate(IvoaSimpleStorageResource)");
        log.debug("Resource [{}][{}]", requested.getMeta(), requested.getClass().getName());

        boolean success = true ;
        IvoaSimpleStorageResource validated = new IvoaSimpleStorageResource()
            .kind(SimpleStorageReservation.KIND_DISCRIMINATOR)
            .meta(
                ValidatorTools.makeMeta(
                    requested.getMeta(),
                    context
                    )
                );
        
        //
        // Check the requested size.
        success &= validateSize(
            requested,
            validated,
            context
            );
        
        //
        // Everything is good, create our Result.
        if (success)
            {
            SimpleStorageReservationValidator.Result result = new SimpleStorageReservationValidator.ResultBean(
                Validator.ResultEnum.ACCEPTED,
                validated
                ){
                @Override
                public AbstractStorageReservationEntity build(final SimpleExecutionSessionEntity session)
                    {
                    this.entity = SimpleStorageReservationValidatorImpl.this.entityFactory.create(
                        session,
                        this
                        );
                    return this.entity;
                    }

                @Override
                public Long getPrepareDuration()    
                    {
                    return SimpleStorageReservationValidatorImpl.this.getPrepareDuration(
                        validated
                        );
                    }

                @Override
                public Long getReleaseDuration()    
                    {
                    return SimpleStorageReservationValidatorImpl.this.getReleaseDuration(
                        validated
                        );
                    }
                };
            //
            // Register the validated result, so that the context knows which
            // storage resources to build. Without this the offer builder has no
            // storage result to build, and a data resource that references one
            // is built with a null storage, which fails the request with a
            // NullPointerException (see issue #178).
            //
            // This also puts the validated result in the context's name map,
            // where the data resource linker looks storage references up, in
            // place of the preliminary result from registration.
            //
            context.addStorageValidatorResult(
                result
                );
            return result;
            }
        //
        // Something wasn't right, fail the validation.
        else {
            context.valid(false);
            return new SimpleStorageReservationValidator.ResultBean(
                ResultEnum.FAILED
                );
            }
        }

    /**
     * Predict the time to prepare the resource.
     * This will be platform dependent, so it should be implemented in the platform specific subclasses.
     * 
     */
    protected abstract Long getPrepareDuration(final IvoaSimpleStorageResource validated);

    /**
     * Predict the time to release the resource.
     * This will be platform dependent, so it should be implemented in the platform specific subclasses.
     * 
     */
    protected abstract Long getReleaseDuration(final IvoaSimpleStorageResource validated);

    /**
     * Validate the requested storage size.
     * This will be platform dependent, so it should be implemented in the platform specific subclasses.
     * 
     */
    protected abstract boolean validateSize(
        final IvoaSimpleStorageResource requested,
        final IvoaSimpleStorageResource validated,
        final OfferSetRequestParserContext context
        );
    }
