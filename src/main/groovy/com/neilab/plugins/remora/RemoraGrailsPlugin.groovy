package com.neilab.plugins.remora

import grails.plugins.*
import org.grails.datastore.gorm.validation.constraints.registry.ConstraintRegistry
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.HibernateDatastore

//import org.grails.orm.hibernate.HibernateDatastore

//import org.grails.orm.hibernate.HibernateDatastore
class RemoraGrailsPlugin extends Plugin {

    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "4.0.0 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
        "grails-app/views/error.gsp",
        "**/com/neilab/plugins/remora/test/**"
    ]

    // TODO Fill in these fields
    def title = "Remora" // Headline display name of the plugin
    def author = "James Whitfield"
    def authorEmail = "valerius@neilab.com"
    def description = '''\
Remora is a Grails Image / File Upload Plugin initially based on the Selfie plugin. Use Remora to attach files to your domain models, upload to a CDN, validate content, produce thumbnails
'''
    def profiles = ['web']

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/remora"

    // Extra (optional) plugin metadata

    // License: one of 'APACHE', 'GPL2', 'GPL3'
    def license = "APACHE"

    // Details of company behind the plugin (if there is one)
    def organization = [ name: "NEiLAB, LLC", url: "http://neilab.com/" ]

    // Any additional developers beyond the author specified above.
    // def developers = [ [ name: "Valerius", email: "valerius@neilab.com" ]]

    // Location of the plugin's issue tracker.
    // def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPMYPLUGIN" ]

    // Online location of the plugin's browseable source code.
    def scm = [ url: "https://github.com/neilabdev/remora" ]

    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional), this event occurs before
    }

    void doWithDynamicMethods() {
        // TODO Implement registering dynamic methods to classes (optional)
        ConstraintRegistry gormValidatorRegistry = grailsApplication.mainContext.getBean("gormValidatorRegistry")

        gormValidatorRegistry.addConstraint(ContentTypeConstraint)
        gormValidatorRegistry.addConstraint(FileSizeConstraint)

        for ( domainClass in grailsApplication.domainClasses) {
            PersistentEntity entity = grailsApplication.mappingContext.getPersistentEntity(domainClass.clazz.name)
            registerRemoraDomain(entity)
        }
    }

    protected registerRemoraDomain(PersistentEntity persistentEntity) {
        def hasAttachments = persistentEntity.persistentProperties.findAll { it.type == Attachment } ?: null
        if (!hasAttachments)
            return

        Remora.registerDomain(persistentEntity)
    }

    Closure doWithSpring() {
        {-> attachmentConverter AttachmentValueConverter }
    }


    void doWithApplicationContext() {
        //HibernateDatastore datastore = applicationContext.getBean(HibernateDatastore.class)
        def datastore = applicationContext.getBean("hibernateDatastore")

        applicationContext.addApplicationListener new AttachmentEventListener(datastore)
        //  grailsApplication.mainContext.eventTriggeringInterceptor?.datastores?.each { k, datastore ->
        //      applicationContext.addApplicationListener new AttachmentEventListener(datastore)
        //  }
    }

    void onChange(Map<String, Object> event) {
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
    }

    void onConfigChange(Map<String, Object> event) {
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }

    void onShutdown(Map<String, Object> event) {
        // TODO Implement code that is executed when the application shuts down (optional)
    }
}
