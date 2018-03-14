package com.neilab.plugins.remora

import grails.core.GrailsDomainClass
import grails.core.GrailsDomainClassProperty
import grails.util.GrailsClassUtils
import org.grails.core.artefact.DomainClassArtefactHandler

class Remora {
    private static Map registeredDomains=[:]
    private static Map registeredProperties = [:]
    private static Map registeredMapping = [:]
    private static Map merge_map(Map[] sources) {
        if (sources.length == 0) return [:]
        if (sources.length == 1) return sources[0]

        sources.inject([:]) { result, source ->
            source.each { k, v ->
                if (result[k] instanceof Map) {
                    result[k] = merge_map(result[k], v)
                } else if (result[k] instanceof List) {
                    result[k] = (result[k] + v).unique()
                } else {
                    result[k] = v
                }
            }
            result
        }
    }
    public static void registerDomain(GrailsDomainClass domainClass) {
        Class clazz = domainClass.clazz

        def merge_map_dsl = { Class domainParam ->
            { Class mappingDomainClass ->
                def mappingDomainSuperClass = mappingDomainClass.superclass
                def fields =
                        GrailsClassUtils.isStaticProperty(mappingDomainClass, "remora") ? mappingDomainClass.remora :
                        GrailsClassUtils.isStaticProperty(mappingDomainClass, "attachments") ?  mappingDomainClass.attachments : null
                def fields_map = fields instanceof Map ? fields : [:] as Map
                def isSuperDomainClass = DomainClassArtefactHandler.isDomainClass(mappingDomainSuperClass)
                def parent_fields_map = [:]
                def combined_map = fields_map
                //todo: verify key values are lists

                if(!isSuperDomainClass) {
                    return fields_map
                } else {
                    parent_fields_map = owner.call(mappingDomainSuperClass)
                    combined_map = parent_fields_map ? merge_map(parent_fields_map, fields_map) : fields_map
                }
                return combined_map
            }.call(domainParam)
        }

        Map remora_mapping =  merge_map_dsl.call(domainClass.clazz)

        registeredMapping[clazz.name] = remora_mapping
        registeredProperties[clazz.name] = domainClass.properties.findAll { it.type == Attachment } ?: null
        registeredDomains[clazz.name] =  true
    }

    public static boolean registeredClass(Class clazz) {
       return registeredClass(clazz.name)
    }

    public static List<GrailsDomainClassProperty> registeredProperties(Class clazz) {
        return registeredProperties(clazz.name)
    }

    public static Map registeredMapping(Class clazz) {
        return registeredMapping(clazz.name)
    }

    public static def config(Class clazz) {
        return config(clazz.name)
    }

    public static boolean registeredClass(String clazzName) {
        registeredDomains.containsKey(clazzName)
    }

    public static List<GrailsDomainClassProperty> registeredProperties(String clazzName) {
        registeredProperties[clazzName]
    }

    public static Map registeredMapping(String clazzName) {
        registeredMapping[clazzName]
    }

    public static def config(String clazzName) {
        registeredDomains[clazzName]
    }


}
