package com.neilab.plugins.remora

import com.neilab.plugins.remora.Attachment
import grails.util.GrailsNameUtils
import grails.util.Holders
import groovy.util.logging.Slf4j

//import groovy.util.logging.Log4j
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener
import org.grails.datastore.mapping.engine.event.*
import org.springframework.context.ApplicationEvent

/**
 * Created by ghost on 7/24/15.
 */
@Slf4j
class AttachmentEventListener extends AbstractPersistenceEventListener {

    private static String OPTIONS_KEY = "remora"

    AttachmentEventListener(final Datastore datastore) {
        super(datastore)
    }

    protected void onPersistenceEvent(final AbstractPersistenceEvent event) {
        boolean hasClass = event?.entityObject ? Remora.registeredClass(event?.entityObject?.class) : null

        if (hasClass) {
            def attachmentFields = attachmentFieldsForEvent(event)
            def entityObject = event.entityObject
            switch (event.eventType) {
                case EventType.SaveOrUpdate:
                    validateAttachment(event, attachmentFields)
                    break
                case EventType.Validation:
                    validateAttachment(event, attachmentFields)
                    break
                case EventType.PreInsert:
                    break
                case EventType.PostInsert:
                    saveAttachment(event, attachmentFields)
                    break
                case EventType.PreUpdate:
                    saveAttachment(event, attachmentFields,true)
                    break
                case EventType.PostUpdate:
                    break
                case EventType.PreDelete:
                    break
                case EventType.PostDelete:
                    postDelete(event, attachmentFields)
                    break
                case EventType.PreLoad:
                    break
                case EventType.PostLoad:
                    postLoad(event, attachmentFields)
                    break
            }
        }
    }

    boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        [PreUpdateEvent, PreInsertEvent,PostLoadEvent, PreDeleteEvent, PostInsertEvent, PostDeleteEvent, SaveOrUpdateEvent, ValidationEvent].contains(eventType)
    }

    void postLoad(final AbstractPersistenceEvent event, attachmentFields) {
        applyPropertyOptions(event.entityObject, attachmentFields)
    }

    void postDelete(final AbstractPersistenceEvent event, attachmentFields) {
        for (attachmentProperty in attachmentFields) {
            def attachment = applyPropertyOption(event.entityObject,attachmentProperty)
            if(!attachment?.isReadOnly) {
                attachment?.delete()
            }
        }
    }

    void validateAttachment(final AbstractPersistenceEvent event, attachmentFields) {
        for (attachmentProperty in attachmentFields) {
            def attachment = applyPropertyOption(event.entityObject,attachmentProperty) as Attachment
            attachment?.verify()
        }
    }

    void saveAttachment(final AbstractPersistenceEvent event, attachmentFields, boolean update = false) {
        for (attachmentProperty in attachmentFields) {
            def entity = event.entityObject
            def persistedAttachment = entity.getPersistentValue(attachmentProperty.name)
            def isDirty = entity.isDirty(attachmentProperty.name)

            if(isDirty) {
                if(persistedAttachment && !(persistedAttachment instanceof Attachment)) {
                    throw new AttachmentException("Attachment for entity '${entity.class.name}' id: '${entity.id}' property: '${attachmentProperty.name}' is not an attachment but a: ${persistedAttachment}")
                }

                if (persistedAttachment && !persistedAttachment.isReadOnly) {
                    applyPropertyOption(event.entityObject,attachmentProperty, attachment: persistedAttachment)?.delete()
                }
            }

            if(!update || isDirty) { //save if 'new' or 'updating'
                applyPropertyOption(event.entityObject,attachmentProperty) ?.save(failOnError: true) //TODO: failOnError configurable?
            }
        }
    }

    static def attachmentFieldsForEvent(final AbstractPersistenceEvent event) {
        event?.entityObject ? Remora.registeredProperties(event.entityObject.class) : null
    }

    static protected Attachment applyPropertyOption(Map params=[:],entityObject,attachmentProperty) {
        def attachment = (params.containsKey("attachment") ? params["attachment"] :
                entityObject."${attachmentProperty.name}" ) as Attachment

        //if(Remora.isCascadingEntity(object: entityObject, field: attachmentProperty.name )) { }

        if (attachment) {
            attachment.parentEntity = entityObject
            attachment.parentPropertyName = attachmentProperty.name
            if(!attachment?.isCopied) {
                attachment.domainName = GrailsNameUtils.getPropertyName(entityObject.getClass()) //if masquerading, this will be parent
                attachment.domainClass = entityObject.getClass().name
                attachment.domainIdentity = entityObject.ident() //if masquerading, this will be parent
                attachment.propertyName = attachmentProperty.name
            }
        }

        return attachment
    }

    static protected applyPropertyOptions(entityObject, attachmentFields) {
        for (attachmentProperty in attachmentFields) {
            applyPropertyOption(entityObject,attachmentProperty)
        }
    }
}
