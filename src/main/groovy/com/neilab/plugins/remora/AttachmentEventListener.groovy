package com.neilab.plugins.remora

import com.neilab.plugins.remora.Attachment
import grails.util.GrailsNameUtils
import grails.util.Holders
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener
import org.grails.datastore.mapping.engine.event.*
import org.springframework.context.ApplicationEvent

/**
 * Created by ghost on 7/24/15.
 */
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
                    //println "EventType.SaveOrUpdate: ${event.entityObject?.class} ${event.entityObject?.id}"
                    validateAttachment(event, attachmentFields)
                    break
                case EventType.Validation:
                    //println "EventType.Validation: ${event.entityObject?.class} ${event.entityObject?.id}"
                    validateAttachment(event, attachmentFields)
                    break
                case EventType.PreInsert:
                    // println "PRE INSERT ${event.entityObject}"
                    break
                case EventType.PostInsert:
                    // println "POST INSERT ${event.entityObject}"
                    //println "EventType.PostInsert: ${event.entityObject?.class} ${event.entityObject?.id}"
                    saveAttachment(event, attachmentFields)
                    break
                case EventType.PreUpdate:
                    // println "PRE UPDATE ${event.entityObject}"
                    //println "EventType.PreUpdate: ${event.entityObject?.class} ${event.entityObject?.id}"
                    saveAttachment(event, attachmentFields)
                    break
                case EventType.PostUpdate:
                    // println "POST UPDATE ${event.entityObject}"
                    //println "EventType.PostUpdate: ${event.entityObject?.class} ${event.entityObject?.id}"
                    break
                case EventType.PreDelete:
                    // println "PRE DELETE ${event.entityObject}"
                  //  postDelete(event, attachmentFields)
                    break
                case EventType.PostDelete:
                    //println "EventType.PostDelete: ${event.entityObject?.class} ${event.entityObject?.id}"
                    postDelete(event, attachmentFields)
                    // println "POST DELETE ${event.entityObject}"
                    break
                case EventType.PreLoad:
                    // println "PRE LOAD ${event.entityObject}"
                    break
                case EventType.PostLoad:
                    // println "POST LOAD ${event.entityObject}"
                    //println "EventType.PostLoad: ${event.entityObject?.class} ${event.entityObject?.id}"
                    postLoad(event, attachmentFields)
                    break
            }
        }
    }

    boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        [PreUpdateEvent, PreInsertEvent,PostLoadEvent, PreDeleteEvent, PostInsertEvent, PostDeleteEvent, SaveOrUpdateEvent, ValidationEvent].contains(eventType)
    }

    void postLoad(final AbstractPersistenceEvent event, attachmentFields) {
        applyPropertyOptions(event, attachmentFields)
    }

    void postDelete(final AbstractPersistenceEvent event, attachmentFields) {
        applyPropertyOptions(event, attachmentFields)
        for (attachmentProperty in attachmentFields) {
            def attachment = event.entityObject."${attachmentProperty.name}"
            attachment?.delete()
        }
    }

    void validateAttachment(final AbstractPersistenceEvent event, attachmentFields) {
        applyPropertyOptions(event, attachmentFields)
        for (attachmentProperty in attachmentFields) {
            def attachment = event.entityObject."${attachmentProperty.name}" as Attachment
          //  def attachmentOptions = event.entityObject."${OPTIONS_KEY}"?."${attachmentProperty.name}"
            attachment?.verify()
        }
    }

    void saveAttachment(final AbstractPersistenceEvent event, attachmentFields) {
        applyPropertyOptions(event, attachmentFields)
        for (attachmentProperty in attachmentFields) {
            def attachment = event.entityObject."${attachmentProperty.name}"
            if (event.entityObject.isDirty(attachmentProperty.name)) {
                def attachmentOptions = event.entityObject."${OPTIONS_KEY}"?."${attachmentProperty.name}"
                def originalAttachment = event.entityObject.getPersistentValue(attachmentProperty.name)
                if (originalAttachment) {
                    originalAttachment.domainName = GrailsNameUtils.getPropertyName(event.entityObject.getClass())
                    originalAttachment.propertyName = attachmentProperty.name
                    originalAttachment.options = attachmentOptions
                    originalAttachment.parentEntity = event.entityObject
                    originalAttachment.delete()
                }
            }
            attachment?.save()
        }
    }

    static def attachmentFieldsForEvent(final AbstractPersistenceEvent event) {
        event?.entityObject ? Remora.registeredProperties(event.entityObject.class) : null
    }

    static protected applyPropertyOptions(event, attachmentFields) {
        for (attachmentProperty in attachmentFields) {
            def attachmentOptions = event.entityObject."${OPTIONS_KEY}"?."${attachmentProperty.name}"
            def attachment = event.entityObject."${attachmentProperty.name}"
            if (attachment) {
                attachment.domainName = GrailsNameUtils.getPropertyName(event.entityObject.getClass())
                attachment.propertyName = attachmentProperty.name
                attachment.options = attachmentOptions
                attachment.parentEntity = event.entityObject
            }
        }
    }

}
