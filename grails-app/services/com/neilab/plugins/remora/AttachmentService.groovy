package com.neilab.plugins.remora

import com.bertramlabs.plugins.karman.*
import com.neilab.plugins.remora.util.RemoraUtil
//import grails.core.GrailsDomainClassProperty
import grails.gorm.transactions.*
import grails.util.Holders
import org.grails.datastore.mapping.model.PersistentProperty


@Transactional
class AttachmentService {

    def grailsApplication
    def grailsDomainClassMappingContext

    @NotTransactional
    Map attachmentInfo(def hostEntitiy, String paramName,String style=RemoraUtil.ORIGINAL_STYLE) {
        RemoraUtil.attachmentInfo(hostEntitiy,paramName,style)
    }

    StorageProvider getStorageProvider(Map params=[:]) {
        def options=[model:null] << params
        def model = options.model


        if(model &&  Remora.registeredClass(model.class)) {

        }

        StorageProvider.create(storageOptions.providerOptions.clone())
    }
    protected  def getConfig() {
        grailsApplication.config?.remora
    }

    protected getStorageOptions(def params=[:]) {
        def options=[
                domainName: null,
                propertyName:null
        ] << params
        def config = getConfig()
        def parentEntity = null
        def domainName = options.domainName
        def propertName = options.propertyName
        def storage_options =  (propertName && domainName ? config?.domain?."${domainName}"?."${propertyName}"?.storage : null)  ?: // grails.plugin.remora.domain.<domain>.<property>.storage ?:
                (domainName ? config?.domain?."${domainName}"?.storage : null) ?: // grails.plugin.remora.domain.<domain>.storage ?:
                        config?.storage ?: [:] // grails.plugin.remora.storage
        if(parentEntity) {
            storage_options = storage_options + (this.parentEntity?."remora"?.storage ?: [:])
            storage_options = storage_options + (options?."${propertyName}"?.storage ?: options?.storage ?: [:])
        }

        storage_options = storage_options.clone()

        if (storage_options.providerOptions && !storage_options.providerOptions.containsKey('defaultFileACL')) {
            storage_options.providerOptions.defaultFileACL = com.bertramlabs.plugins.karman.CloudFileACL.PublicRead
        }

        if (!storage_options.containsKey('path')) {
            storage_options.path = ':class/:id/:propertyName/'
        }

        storage_options
    }


    @NotTransactional
    def getAt(def hostEntity) {
        if (! Remora.registeredClass(hostEntity.getClass()))
            throw new IllegalArgumentException("element must be a domain class with an :attachment")

        //def domainClass = grailsApplication.getDomainClass(hostEntity.getClass().getName()) as GrailsDomainClass
        def attachments = Remora.registeredProperties(hostEntity.getClass())//domainClass.properties.findAll { it.type == Attachment } ?: []
        def info = [:]


        attachments.each { PersistentProperty property ->
            def name = property.name
            info[name] = attachmentInfo(hostEntity,name,null)
        }

        info
    }
}
