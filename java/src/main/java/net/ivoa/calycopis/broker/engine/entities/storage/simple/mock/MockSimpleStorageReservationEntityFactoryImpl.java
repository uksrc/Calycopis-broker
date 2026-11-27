/*
 *
 * AIMetrics: [
 *     {
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */
package net.ivoa.calycopis.broker.engine.entities.storage.simple.mock;

import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.component.AbstractEntityRepository;
import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.AbstractStorageReservationEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.AbstractStorageReservationValidator;
import net.ivoa.calycopis.broker.engine.entities.storage.simple.SimpleStorageReservationEntityFactoryImpl;

/**
 *
 */
@Slf4j
public class MockSimpleStorageReservationEntityFactoryImpl
extends SimpleStorageReservationEntityFactoryImpl
implements MockSimpleStorageReservationEntityFactory
    {

    /**
     * Public constructor used by our Platform.
     * 
     */
    public MockSimpleStorageReservationEntityFactoryImpl(
        final AbstractEntityRepository<AbstractStorageReservationEntity> repository
        ){
        super(
            repository
            );
        }

    @Override
    public MockSimpleStorageReservationEntity create(
        final SimpleExecutionSessionEntity session,
        final AbstractStorageReservationValidator.Result result
        ){
        MockSimpleStorageReservationEntity entity = this.repository.save(
            new MockSimpleStorageReservationEntity(
                session,
                result
                )
            );
        return entity ;
        }
    }
