package com.neilab.plugins.remora.util

import com.neilab.plugins.remora.Attachment
import grails.util.GrailsClassUtils
import grails.util.GrailsNameUtils
import grails.util.Holders

/**
 * Created by ghost on 8/3/15.
 */
class RemoraUtil {

    static String ORIGINAL_STYLE = 'original'
    private static def STYLE_OVERRIDES = [
            original: ''
    ]

    static def getConfig() {
        Holders.config?.grails.plugin.remora ?: Holders.config?.remora
    }

    static List pathComponents(String path) {
        def parts = path.split("/")
        def fileName = parts[-1]
        def path_components = parts?.length > 1 ? "/${parts[0..(parts.length - 2)].findAll { it }.join('/')}" : "/"
        [path_components, fileName]
    }

    static Map attachmentInfo(def hostEntity, String paramName, String style = ORIGINAL_STYLE) {
        Attachment attachment = hostEntity[paramName]
        if(!attachment) {
            return null
        }
        attachmentInfo(hostEntity, (Attachment)attachment, style)
    }

    static Map attachmentInfo(def hostEntity, Attachment attachment, String style = ORIGINAL_STYLE) {
        def currentStyle = style ?: ORIGINAL_STYLE
        def storageOptions = storageOptions(hostEntity, attachment)
        def bucket = storageOptions.bucket ?: '.'
        def path = storageOptions.path ?: ''
        def typeFileName = fileNameForType(attachment, style: currentStyle)
        def parsedPath = evaluatePath(path, hostEntity, attachment, currentStyle)?.replaceAll(/\/$/, '')
        def cloudPath = joinPath(parsedPath, typeFileName)

        [
                bucket   : bucket,
                directory: parsedPath,
                prefix   : parsedPath,
                name     : typeFileName,
                path     : cloudPath,
                options  : storageOptions
        ]
    }

    static String fileNameForType(Map params = [:], Attachment attachment) {
        def name = attachment.name
        def options = [style: ORIGINAL_STYLE] << params
        def styleName = options.style
        def format = attachment.options?.styles?."${styleName}"?.format
        def styleOverride = STYLE_OVERRIDES.containsKey(styleName) ? STYLE_OVERRIDES[styleName] : styleName
        def saneFileName = name.replaceAll(/[^a-zA-Z0-9\_\-\.\/]/, '_')
        def fileNameWithOutExt = saneFileName.replaceFirst(/[.][^.]+$/, "")
        def matcher = (saneFileName =~ /[.]([^.]+)$/)
        def extension = format ?: options.format ?: (matcher ? "${matcher[0][1]}" : '')
        def suffix = extension ? ".${extension}" : ''
        def fileName = styleOverride ?
                "${fileNameWithOutExt}_${styleOverride}${suffix}" :
                "${fileNameWithOutExt}${suffix}"
        return fileName
    }

    static String evaluatePath(String path,
                                      def hostEntity, Attachment attachment, String styleName = ORIGINAL_STYLE) {
        Class clazz = getEntityClass(hostEntity,attachment)
        def parentIdentity = getEntityIdentity(hostEntity,attachment)// ?: attachment.domainIdentity
        String propertyName = attachment.propertyName
        String style = styleName ?: 'original' // change 'original' to originalStyle .. this should never be null
        path?.replace(":class", "${GrailsNameUtils.getShortName(clazz)}").
                replace(":domainName", "${GrailsNameUtils.getPropertyName(clazz.simpleName)}").
                replace(":id", "${parentIdentity}").
                replace(":type", "${style}").
                replace(":style", "${style}").
                replace(":propertyName", "${propertyName}") // :class/:domainName/:style/:propertyName/:id/:type
    }

    static String joinPath(String prefix, String suffix) {
        String p = prefix?.replaceAll(/^\/|\/$/, '') ?: ''
        String s = suffix?.replaceAll(/^\/|\/$/, '') ?: ''
        [p, s].findAll { it }.join('/')
    }

    static Map storageOptions(Map params = [:], def hostEntity, Attachment persistedAttachment) {
        def options = [:] << params
        Class parentClass = getEntityClass(hostEntity,persistedAttachment)
        storageOptions(parentClass, persistedAttachment.domainName,
                persistedAttachment.propertyName, persistedAttachment.options + options)
    }

    static Map storageOptions(Class clazz, String domainName, String propertyName, Map attachmentOptions) {
        def options = attachmentOptions
        def config = getConfig()
        def storage_options = config?.domain?."${domainName}"?."${propertyName}"?.storage ?: // grails.plugin.remora.domain.<domain>.<property>.storage ?:
                config?.domain?."${domainName}"?.storage ?: // grails.plugin.remora.domain.<domain>.storage ?:
                        config?.storage ?: [:] // grails.plugin.remora.storage
        if (GrailsClassUtils.isStaticProperty(clazz, "remora") && clazz?."remora" instanceof Map) {
            Map instanceOptions = clazz?."remora"
            storage_options = storage_options + (instanceOptions.storage ?: [:]) // Model.remora.storage
            storage_options = storage_options + (instanceOptions."${propertyName}"?.storage ?: [:]) // Model.remora.storage
        }
        storage_options = storage_options + (options?."${propertyName}"?.storage ?: options?.storage ?: [:])
        //Model.remora.properyName.storage
        storage_options = storage_options.clone()

        if (storage_options.providerOptions && !storage_options.providerOptions.containsKey('defaultFileACL')) {
            storage_options.providerOptions.defaultFileACL = com.bertramlabs.plugins.karman.CloudFileACL.PublicRead
        }

        if (!storage_options.containsKey('path')) {
            storage_options.path = ":domainName/:propertyName/:id/:style"
        }

        storage_options
    }

    static def getEntityIdentity(def entity, Attachment attachment=null) {
        if(attachment?.isPersisted)
            return attachment.domainIdentity ?: entity?.ident()
        return entity?.ident()
    }

    static Class getEntityClass(def type, Attachment attachment=null) {
        if(attachment?.isCopied)
            return Class.forName(attachment.domainClass)
        if (type instanceof String) {
            return Class.forName(type)
        } else  {
            return type.getClass()
        }
    }
}
