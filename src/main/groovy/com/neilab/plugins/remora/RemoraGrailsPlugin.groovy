package com.neilab.plugins.remora

import grails.core.GrailsDomainClass;
import grails.plugins.*
import grails.util.GrailsClassUtils
import grails.validation.ConstrainedProperty

class RemoraGrailsPlugin extends Plugin {

    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "3.1.9 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
        "grails-app/views/error.gsp",
        "**/remora/test/**"
    ]

    // TODO Fill in these fields
    def title = "Remora" // Headline display name of the plugin
    def author = "James Whitfield"
    def authorEmail = ""
    def description = '''\
Brief summary/description of the plugin.
'''
    def profiles = ['web']

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/remora"

    // Extra (optional) plugin metadata

    // License: one of 'APACHE', 'GPL2', 'GPL3'
//    def license = "APACHE"

    // Details of company behind the plugin (if there is one)
//    def organization = [ name: "My Company", url: "http://www.my-company.com/" ]

    // Any additional developers beyond the author specified above.
//    def developers = [ [ name: "Joe Bloggs", email: "joe@bloggs.net" ]]

    // Location of the plugin's issue tracker.
//    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPMYPLUGIN" ]

    // Online location of the plugin's browseable source code.
//    def scm = [ url: "http://svn.codehaus.org/grails-plugins/" ]

    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional), this event occurs before
    }

    void doWithDynamicMethods() {
        // TODO Implement registering dynamic methods to classes (optional)
        ConstrainedProperty.registerNewConstraint('contentType', ContentTypeConstraint)
        ConstrainedProperty.registerNewConstraint('fileSize', FileSizeConstraint)
        for (GrailsDomainClass domainClass in grailsApplication.domainClasses) {
            registerRemoraDomain(domainClass)
        }
    }

    protected registerRemoraDomain(GrailsDomainClass domainClass) {
        def hasAttachments = domainClass.properties.findAll { it.type == Attachment } ?: null
        if (!hasAttachments)
            return

        Remora.registerDomain(domainClass)
    }

    Closure doWithSpring() {
        {-> attachmentConverter AttachmentValueConverter }
    }


    void doWithApplicationContext() {
        grailsApplication.mainContext.eventTriggeringInterceptor.datastores.each { k, datastore ->
            applicationContext.addApplicationListener new AttachmentEventListener(datastore)
        }
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
