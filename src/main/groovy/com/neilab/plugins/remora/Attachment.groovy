package com.neilab.plugins.remora

import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.StorageProvider
import com.bertramlabs.plugins.karman.util.Mimetypes
import com.neilab.plugins.remora.processors.ImageResizer
import com.neilab.plugins.remora.util.RemoraUtil
import grails.util.GrailsNameUtils
import grails.util.Holders
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.springframework.validation.FieldError
import org.springframework.web.multipart.MultipartFile
import grails.validation.Validateable

class Attachment implements Serializable, Validateable  {

    public static enum CascadeType {
        ALL, // default PERSIST|REMOVE
        PERSIST, // save or update should persist file to storage location
        REMOVE,  // removing Parent shoudl remove attachement
        NONE,
        READONLY
    }


    private String ORIGINAL_STYLE = RemoraUtil.ORIGINAL_STYLE
    String name
    String originalFilename
    String contentType
    Long size
    String propertyName // name of attachment Model.attachmentPropertyName
    String domainName //name of domain attached to

    def options = [:]
    def overrides = [:]
    def parentEntity
    def processors = [ImageResizer]

    InputStream fileStream
    byte[] fileBytes

    private static serialProperties = ['name', 'originalFilename', 'contentType', 'size', 'propertyName', 'domainName']

    static validateable = serialProperties

    static constraints = {
        name blank: false, nullable: false
        originalFilename blank: false, nullable: false
        contentType blank: false, nullable: false
        propertyName nullable: false
        domainName nullable: false
    }

    def beforeValidate() {
        assignAttributes()
    }

    public Attachment(String jsonText) {
        // attachmentProperties = jsonText ? new JsonSlurper().parseText(jsonText) : [:]
        if (jsonText)
            fromJson(jsonText)
    }

    public Attachment(Map map) {
        map?.each { k, v -> this[k] = v }
    }

    public Attachment(Map map=[:],MultipartFile file) {
        contentType = file.contentType
        name = originalFilename = file.originalFilename
        size = file.size
        fileStream = file.inputStream
        map?.each { k, v -> this[k] = v }
    }

    public Attachment(InputStream stream, fileName,mimeType = null) {
        size = stream.available()
        fileStream=stream
        name = originalFilename = fileName
        contentType = mimeType ?: Mimetypes.instance.getMimetype(name.toLowerCase())
    }

    public Attachment(Map map=[:],File file) {
        originalFilename = name = file.name
        size = file.size()
        contentType = Mimetypes.instance.getMimetype(name.toLowerCase())
        fileStream = file.newInputStream()
        map?.each { k, v -> this[k] = v }
    }

    def verify() {
        validate(validateable) //todo: optimize so that validation only called per request/update/validate/save

        if(this.hasErrors()) {
            this.errors?.allErrors?.each { FieldError err ->
                this.parentEntity?.errors.reject(
                        "${GrailsNameUtils.getPropertyName(this.parentEntity.class.simpleName)}.${this.propertyName}.${err.field}.invalid" , // 'user.password.doesnotmatch',
                        err.arguments,
                        err.defaultMessage)
            }
        }
    }

    def fromJson(String jsonText) {
        def jsonSlurper = new JsonSlurper()
        Map jsonMap = jsonSlurper.parseText(jsonText)
        jsonMap.each { k, v -> if (serialProperties.contains(k)) this[k] = v }
    }

    def toJson() {
        Map p = [:]
        serialProperties.each { name ->
            p[name] = this[name]
        }
        JsonOutput.toJson(p)
    }


    def getUrl() {
        url(ORIGINAL_STYLE)
    }

    def url(typeName=ORIGINAL_STYLE, expiration = null) {
        def storageOptions = getStorageOptions()
        def typeFileName = fileNameForType(typeName)
        def cloudFile = getCloudFile(typeName)
        def url

        if (!storageOptions.url) {
            url = cloudFile.getURL(expiration)?.toString()
        } else {
            url = joinPath(evaluatedPath((storageOptions.url ?: '/'), typeName),typeFileName)
        }

        url
    }

    Map getOptions() {
        def evaluatedOptions = overrides ? overrides.clone() + options?.clone() : options?.clone()
        if (evaluatedOptions?.styles && evaluatedOptions?.styles instanceof Closure) {
            evalutedOptions.styles = evaluatedOptions.styles.call(attachment)
        }
        evaluatedOptions?.styles?.each { style ->
            if (style.value instanceof Closure) {
                style.value = style.value.call(attachment)
            }
        }
        return evaluatedOptions
    }

    void setInputStream(is) {
        fileStream = is
    }

    def getInputStream() {
        cloudFile.inputStream
    }

    void save() {
        def storageOptions = getStorageOptions()
        def bucket = storageOptions.bucket ?: '.'
        def path = storageOptions.path ?: ''
        def providerOptions = storageOptions.providerOptions?.clone() ?: [:] //FIXME: If providerOptions required and not present, throw exception noting the issue
        def provider = StorageProvider.create(providerOptions)
        def providerPath = joinPath(evaluatedPath(path, ORIGINAL_STYLE),fileNameForType(ORIGINAL_STYLE))
        def originalStyle  = this.options?.styles?."${ORIGINAL_STYLE}"
        def mimeType = Mimetypes.instance.getMimetype(name?.toLowerCase())
        def isImage = mimeType.startsWith("image")
        //TODO: DETERMINE IF SAVE SHOULD PERSIST
        // First lets upload the original
        if (fileStream && name) {
            fileBytes = fileStream.bytes
            size = fileBytes.length
            assignAttributes()

            if(isImage) {
                if(!originalStyle)
                    provider[bucket][providerPath] = fileBytes
                runAttachmentProcessors()
            } else {
                provider[bucket][providerPath] = fileBytes
            }
        }
        fileBytes = fileStream = null
    }

    def saveProcessedStyle(typeName, byte[] bytes) {
        def cloudFile = getCloudFile(typeName)
        def mimeType = Mimetypes.instance.getMimetype(cloudFile.name.toLowerCase())

        if([ORIGINAL_STYLE].contains(typeName)) {
            size = bytes.length
            assignAttributes()
        }

        if(mimeType) {
            cloudFile.contentType = mimeType
        }

        cloudFile.bytes = bytes
        cloudFile.save()
    }

    void delete() {
        def storageOptions = getStorageOptions()
        def path = storageOptions.path ?: ''
        def provider = StorageProvider.create(storageOptions.providerOptions.clone())
        def bucket = storageOptions.bucket ?: '.'
        //TODO: DETERMINE IF DELTE SHOULD PERSIST

        for (type in styles) {
            def joinedPath = joinPath(evaluatedPath(path, type), fileNameForType(type))
            def cloudFile = provider[bucket][joinedPath]
            if (cloudFile.exists()) {
                cloudFile.delete()
            }
        }
    }

    def getStyles() {
        def types = [ORIGINAL_STYLE]
        types.addAll options?.styles?.collect { it.key } ?: []
        types
    }

    void setOriginalFilename(String originalName) {
        originalFilename = originalName
        name = name ?: originalFilename
    }

    CloudFile getCloudFile(typeName = ORIGINAL_STYLE) {
        if (!typeName) {
            typeName = ORIGINAL_STYLE
        }
        Map attachmentInfo = RemoraUtil.attachmentInfo(this.parentEntity,this,typeName)
        def storageOptions = attachmentInfo.options
        def bucket = storageOptions.bucket ?: '.'
        def cloudPath = attachmentInfo.path
        def providerOptions = storageOptions.providerOptions.clone()
        def provider = StorageProvider.create(providerOptions)
        return provider[bucket][cloudPath]
    }

    String getPrefix(typeName = ORIGINAL_STYLE) {
        if (!typeName) {
            typeName = ORIGINAL_STYLE
        }
        Map attachmentInfo = RemoraUtil.attachmentInfo(this.parentEntity,this,typeName)
        def storageOptions = attachmentInfo.options
        attachmentInfo.prefix
    }

    protected def assignAttributes() {
        this.options.assign?.each { k, v ->
            if (this.hasProperty(k) && this.parentEntity?.hasProperty(v)) {
                this.parentEntity[v] = this[k]
            }
        }
    }

    protected String fileNameForType(typeName=ORIGINAL_STYLE) {
        RemoraUtil.fileNameForType(this,style:typeName)
    }

    protected  String joinPath(String prefix, String suffix) {
        RemoraUtil.joinPath(prefix,suffix)
    }

    protected void runAttachmentProcessors() {
        for (processorClass in processors) {
            processorClass.newInstance(attachment: this).process()
        }
        // TODO: Grab Original File and Start Building out Thumbnails
    }

    protected static def getConfig() {
        Holders.config?.remora
    }

    protected getStorageOptions() {
        RemoraUtil.storageOptions(this.parentEntity,this)
    }

    protected String evaluatedPath(String input, type = ORIGINAL_STYLE) {
        RemoraUtil.evaluatePath(input,parentEntity,propertyName,type)
    }
}
