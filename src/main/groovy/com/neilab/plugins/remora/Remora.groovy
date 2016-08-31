package com.neilab.plugins.remora

import grails.core.GrailsDomainClass
import grails.core.GrailsDomainClassProperty
import grails.util.GrailsClassUtils

class Remora {
    private static Map registeredDomains=[:]
    private static Map registeredProperties = [:]

    public static void registerDomain(GrailsDomainClass domainClass) {
        Class clazz = domainClass.clazz
        def attachable = GrailsClassUtils.isStaticProperty(clazz, "attachments") ? clazz.attachments : null
        def enableRemora = GrailsClassUtils.isStaticProperty(clazz, "remora") ? clazz.remora : null

        registeredProperties[domainClass.clazz] = domainClass.properties.findAll { it.type == Attachment } ?: null
        registeredDomains[clazz] = enableRemora ?: attachable ?: true
      //  this.registerClass(domainClass.clazz)
    }

    public static void registerClass(Class clazz) {
        def attachable = GrailsClassUtils.isStaticProperty(clazz, "attachments") ? clazz.attachments : null
        def enableRemora = GrailsClassUtils.isStaticProperty(clazz, "remora") ? clazz.remora : null


        registeredDomains[clazz] = enableRemora ?: attachable ?: true
    }

    public static boolean registeredClass(Class clazz) {
        registeredDomains.containsKey(clazz)
    }

    public static List<GrailsDomainClassProperty> registeredProperties(Class clazz) {
        registeredProperties[clazz]
    }

    public static def config(Class clazz) {
        registeredDomains[clazz]
    }


}
