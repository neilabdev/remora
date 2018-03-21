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

class Attachment implements Serializable, Validateable {

    static enum CascadeType {
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
    String parentPropertyName
    String propertyName // name of attachment Model.attachmentPropertyName
    String domainName //name of domain attached to
    String domainClass
    Boolean domainCopied
    def domainIdentity

    def options = [:]
    def overrides = [:]
    def parentEntity
    def processors = [ImageResizer]

    InputStream fileStream
    byte[] fileBytes
    protected boolean persisted = false
    //protected boolean copied = false

    private static serialProperties = ['name', 'originalFilename', 'contentType', 'size',
                                       'propertyName', 'domainName', 'domainClass', 'domainIdentity', 'domainCopied']

    static validateable = serialProperties

    static constraints = {
        name blank: false, nullable: false
        originalFilename blank: false, nullable: false
        contentType blank: false, nullable: false
        propertyName nullable: false
        domainName nullable: false
        domainClass nullable: true, validator: { val, Attachment obj ->
            if (obj.isCopied && obj.parentPropertyName) { //TODO: Refactor to helper methods.. make cleaner
                def registerdMapping = Remora.registeredMapping(obj.parentEntityClass)
                def requiredClass = registerdMapping?."${obj.parentPropertyName}"?.as
                if (requiredClass) {
                    return RemoraUtil.getIsMatchingTypes(requiredClass, obj.domainClass)
                }
                return true
            }
            true
        } //should assume parentEntity class if null
        domainCopied nullable: true
        domainIdentity nullable: true
        parentPropertyName nullable: true
    }

    def beforeValidate() {
        assignAttributes()
    }

    protected Attachment(Attachment copy) {

        serialProperties.each { name ->
            this[name] = copy[name]
        }
        this.persisted = copy.isPersisted
        this.options = copy.options
        this.overrides = copy.overrides
        this.domainCopied = true
    }

    Attachment(String jsonText) { //set readonly
        // attachmentProperties = jsonText ? new JsonSlurper().parseText(jsonText) : [:]
        if (jsonText)
            fromJson(jsonText)
    }

    Attachment(Map map) {
        map?.each { k, v -> this[k] = v }
    }

    Attachment(Map map = [:], MultipartFile file) {
        contentType = file.contentType
        name = originalFilename = file.originalFilename
        size = file.size
        fileStream = file.inputStream
        map?.each { k, v -> this[k] = v }
    }

    Attachment(InputStream stream, fileName, mimeType = null) {
        size = stream.available()
        fileStream = stream
        name = originalFilename = fileName
        contentType = mimeType ?: Mimetypes.instance.getMimetype(name.toLowerCase())
    }

    Attachment(Map map = [:], File file) {
        originalFilename = name = file.name
        size = file.size()
        contentType = Mimetypes.instance.getMimetype(name.toLowerCase())
        fileStream = file.newInputStream()
        map?.each { k, v -> this[k] = v }
    }

    def verify() {
        //   if(this.isPersisted)
        //       return
        validate(validateable) //todo: optimize so that validation only called per request/update/validate/save

        if (this.hasErrors()) {
            this.errors?.allErrors?.each { FieldError err ->
                this.parentEntity?.errors.reject(
                        "${GrailsNameUtils.getPropertyName(this.parentEntity.class.simpleName)}.${this.propertyName}.${err.field}.invalid", // 'user.password.doesnotmatch',
                        err.arguments,
                        err.defaultMessage)
            }
        }
    }

    def fromJson(String jsonText) {
        def jsonSlurper = new JsonSlurper()
        Map jsonMap = jsonSlurper.parseText(jsonText)
        jsonMap.each { k, v -> if (serialProperties.contains(k)) this[k] = v }
        this.persisted = true
    }

    def toJson() {
        Map p = [:]
        serialProperties.each { name ->
            if (this[name] != null)
                p[name] = this[name]
        }

        JsonOutput.toJson(p)
    }


    def getUrl() {
        url(ORIGINAL_STYLE)
    }

    def url(typeName = ORIGINAL_STYLE, expiration = null) {
        def storageOptions = getStorageOptions()
        def typeFileName = fileNameForType(typeName)
        def cloudFile = getCloudFile(typeName)
        def url

        if (!storageOptions.url) {
            url = cloudFile.getURL(expiration)?.toString()
        } else {
            url = joinPath(evaluatedPath((storageOptions.url ?: '/'), typeName), typeFileName)
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

    boolean save(Map params = [:])  {//} throws AttachmentException {
        def options = [failOnError: false] << params
        def storageOptions = getStorageOptions()
        def bucket = storageOptions.bucket ?: '.'
        def path = storageOptions.path ?: ''
        def providerOptions = storageOptions.providerOptions?.clone() ?: [:]
        //FIXME: If providerOptions required and not present, throw exception noting the issue
        def provider = StorageProvider.create(providerOptions)
        def providerPath = joinPath(evaluatedPath(path, ORIGINAL_STYLE), fileNameForType(ORIGINAL_STYLE))
        def originalStyle = this.options?.styles?."${ORIGINAL_STYLE}"
        def mimeType = Mimetypes.instance.getMimetype(name?.toLowerCase())
        def isImage = mimeType.startsWith("image")
        def success = false
        //TODO: DETERMINE IF SAVE SHOULD PERSIST
        // First lets upload the original
        if (fileStream && name) {
            fileBytes = fileStream.bytes
            size = fileBytes.length
            assignAttributes()

            if (isImage) {
                if (!originalStyle) {
                    provider[bucket][providerPath] = fileBytes
                    success = provider[bucket][providerPath].exists()
                }
                success = runAttachmentProcessors()
            } else {
                provider[bucket][providerPath] = fileBytes
                success = provider[bucket][providerPath].exists()
            }
        } else if(this.isCopied) { //TODO: why is this called if  persisted?
            success = true
        } else if(this.isPersisted) {
            success = true // NOTE: Should never be called, for debugging
        }


        if (success) {
            fileBytes = fileStream = null
            persisted = true
        } else if (options.failOnError) {
            fileBytes = null
            throw new AttachmentException("Unable to save attachment named '${this.name}'" as String)
        }

        return success
    }

    boolean saveProcessedStyle(typeName, byte[] bytes) {
        def cloudFile = getCloudFile(typeName)
        def mimeType = Mimetypes.instance.getMimetype(cloudFile.name.toLowerCase())
        def success = false
        if ([ORIGINAL_STYLE].contains(typeName)) {
            size = bytes.length
            assignAttributes()
        }

        if (mimeType) {
            cloudFile.contentType = mimeType
        }

        cloudFile.bytes = bytes
        cloudFile.save() //thor exception if not exits
        success = cloudFile.exists()
        return success
    }

    void delete() {
        def storageOptions = getStorageOptions()
        def path = storageOptions.path ?: ''
        def provider = StorageProvider.create(storageOptions.providerOptions.clone())
        def bucket = storageOptions.bucket ?: '.'
        //TODO: DETERMINE IF DELTE SHOULD PERSIST

        if(this.isReadOnly)
            throw new IllegalStateException("Attempted to delete readonly Attachment")

        for (type in styles) {
            def joinedPath = joinPath(evaluatedPath(path, type), fileNameForType(type))
            def cloudFile = provider[bucket][joinedPath]
            if (cloudFile.exists()) {
                cloudFile.delete()
            }
        }
    }

    boolean exists() {
        return this.getCloudFile().exists()
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
        Map attachmentInfo = RemoraUtil.attachmentInfo(this.parentEntity, this, typeName)
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
        Map attachmentInfo = RemoraUtil.attachmentInfo(this.parentEntity, this, typeName)
        def storageOptions = attachmentInfo.options
        attachmentInfo.prefix
    }

    Attachment getCopy() {
        if (!this.isPersisted)
            throw new IllegalStateException("Attempting to copy an Attachment that is not persisted")
        return new Attachment(this)
    }

    boolean getIsPersisted() {
        return persisted
    }

    boolean getIsReadOnly() { //TODO: Add logic to determine what is readonly... otherwise, Atachment should be deleted
        return this.isCopied
    }

    boolean getIsCopied() {
        return this.domainCopied ?: false
    }

    protected def assignAttributes() {
        if (this.isCopied)  //TODO: assign should be local to parent enity (or not supported by copies
            return
        this.options.assign?.each { k, v ->
            if (owner.hasProperty(k) && owner.parentEntity?.hasProperty(v)) {
                def value = owner[k]
                owner.parentEntity[v] = value
            }
        }
    }

    protected String fileNameForType(typeName = ORIGINAL_STYLE) {
        RemoraUtil.fileNameForType(this, style: typeName)
    }

    protected String joinPath(String prefix, String suffix) {
        RemoraUtil.joinPath(prefix, suffix)
    }

    protected boolean runAttachmentProcessors() {
        boolean success = true
        for (processorClass in processors) {
            if (success && !processorClass.newInstance(attachment: this).process())
                success = false
        }
        // TODO: Grab Original File and Start Building out Thumbnails
        return success
    }

    protected static def getConfig() {
        Holders.config?.remora
    }

    protected getStorageOptions() {
        RemoraUtil.storageOptions(this.parentEntity, this)
    }

    protected String evaluatedPath(String input, type = ORIGINAL_STYLE) {
        RemoraUtil.evaluatePath(input, parentEntity, this, type)
    }

    protected Class getParentEntityClass() {
        return RemoraUtil.getEntityClass(this.parentEntity)
    }
}
