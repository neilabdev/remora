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

import java.nio.file.Files
import java.nio.file.StandardCopyOption

class AttachmentFile {
    File file
    MultipartFile multipartFile
    File tempFile
    String fileName
    String contentType

    AttachmentFile(InputStream stream, String fileName, String contentType = null) {
        this.tempFile = File.createTempFile("remora-", "-attatchmentFile")
        Files.copy(stream,tempFile.toPath(),StandardCopyOption.REPLACE_EXISTING)
        tempFile.deleteOnExit()
        this.file = this.tempFile
        this.fileName = fileName
        this.contentType = contentType
    }

    AttachmentFile(File file) {
        this.file  = file
    }

    AttachmentFile(MultipartFile file) {
        this.multipartFile = file
    }

    InputStream getInputStream() {
        return this.file?.newDataInputStream() ?: this.multipartFile?.inputStream
    }

    Long getSize() {
        return this.file?.size() ?: this.multipartFile?.size 
    }

    String getName() {
        return this.fileName ?: this.file?.name ?: this.multipartFile?.originalFilename
    }

    String getContentType() {
        return this.contentType ?:
                (this.file ?  Mimetypes.instance.getMimetype(file.name.toLowerCase()) : this.multipartFile.contentType)
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize()
        this.tempFile?.delete()
    }

    void close() {
        this.tempFile?.delete()
        this.tempFile = null
    }
}

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
    Long width = 0
    Long height = 0
    String parentPropertyName
    String propertyName // name of attachment Model.attachmentPropertyName
    String domainName //name of domain attached to
    String domainClass
    Boolean domainCopied
    def domainIdentity
    def parentEntity
    def processors = [ImageResizer]
    private AttachmentFile attachmentFile
    protected boolean persisted = false


    private static serialProperties = ['name', 'originalFilename', 'contentType', 'size', 'width','height',
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
                def registeredMapping = Remora.registeredMapping(obj.parentEntityClass)
                def requiredClass = registeredMapping?."${obj.parentPropertyName}"?.as
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
        width nullable: true
        height nullable: true
    }

    def beforeValidate() {
        assignAttributes()
    }

    protected Attachment(Attachment copy) {

        serialProperties.each { name ->
            this[name] = copy[name]
        }
        this.persisted = copy.isPersisted
        // this.options = copy.options
        this.domainCopied = true
    }

    Attachment(String jsonText) {
        if (jsonText)
            fromJson(jsonText)
    }

    Attachment(Map map) {
        map?.each { k, v -> this[k] = v }
    }

    @Deprecated
    Attachment(InputStream stream, fileName, mimeType = null) {
        this.attachmentFile = new AttachmentFile(stream,mimeType)
        name = originalFilename = fileName
        contentType = mimeType ?: Mimetypes.instance.getMimetype(name.toLowerCase())
    }

    Attachment(Map map = [:], MultipartFile file) {
        attachmentFile = new AttachmentFile(file)
        name = originalFilename = file.originalFilename
        size = file.size

        if([Mimetypes.MIMETYPE_OCTET_STREAM].contains(file.contentType)) {
            contentType = Mimetypes.instance.getMimetype(name)
        } else {
            contentType = file.contentType
        }

        map?.each { k, v -> this[k] = v }
    }

    Attachment(Map map = [:], File file) {
        attachmentFile = new AttachmentFile(file)
        originalFilename = name = file.name
        size = file.size()
        contentType = Mimetypes.instance.getMimetype(name.toLowerCase())
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

    String getUrl() {
        url(ORIGINAL_STYLE)
    }

    String url(typeName = ORIGINAL_STYLE, expiration = null) {
        def storageOptions = getAttachmentStorageOptions()
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

    private Map cachedOptions = null
    Map getOptions() {
        if(cachedOptions)
            return  cachedOptions

        def evaluatedOptions = [:] + attachmentOptions

        if (evaluatedOptions?.styles && evaluatedOptions?.styles instanceof Closure) {
            evaluatedOptions.styles = evaluatedOptions.styles.call(this)
        }

        evaluatedOptions?.styles?.each { style ->
            if (style.value instanceof Closure) {
                style.value = style.value.call(this)
            }
        }

        cachedOptions = evaluatedOptions
        return evaluatedOptions
    }
/*
    void setInputStream(InputStream stream) {
        return
    } */

    InputStream getInputStream() {
        this.isPersisted ? cloudFile.inputStream : this.attachmentFile?.inputStream
    }

    boolean save(Map params = [:])  {//} throws AttachmentException {
        def opts = [failOnError: false] << params
        def storageOptions = getAttachmentStorageOptions()
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

        if (attachmentFile) {
            size = attachmentFile.size
            assignAttributes()

            if (isImage) {
                if (!originalStyle) {
                    CloudFile providerCouldFile = provider[bucket][providerPath]
                    providerCouldFile.setContentLength(attachmentFile.size)
                    providerCouldFile.setInputStream(attachmentFile.inputStream)
                    providerCouldFile.save()
                    if(provider[bucket][providerPath].exists()) {
                        success = runAttachmentProcessors()
                    }
                } else {
                    success = runAttachmentProcessors()
                }
            } else {
                CloudFile providerCouldFile = provider[bucket][providerPath]
                providerCouldFile.setContentLength(attachmentFile.size)
                providerCouldFile.setInputStream(attachmentFile.inputStream)
                providerCouldFile.save()
                success = provider[bucket][providerPath].exists()
            }
        } else if(this.isCopied) { //TODO: why is this called if  persisted?
            success = true
        } else if(this.isPersisted) {
            success = true // NOTE: Should never be called, for debugging
        }

        if (success) {
            persisted = true
            this.attachmentFile?.close()
            this.attachmentFile = null
        } else if (opts.failOnError) {
            throw new AttachmentException("Unable to save attachment named '${this.name}'" as String)
        }

        return success
    }

    protected boolean saveProcessedStyle(Map params=[:],typeName, File file) {
        def cloudFile = getCloudFile(typeName)
        def mimeType = Mimetypes.instance.getMimetype(cloudFile.name.toLowerCase())
        def success = false
        long fileSize = file.size()


        if ([ORIGINAL_STYLE].contains(typeName)) {
            size =  params.size ?: fileSize
            width = params.width ?: 0
            height = params.height ?: 0
            assignAttributes()
        }

        if (mimeType) {
            cloudFile.contentType = mimeType
        }

        cloudFile.setContentLength(fileSize)
        cloudFile.inputStream = new BufferedInputStream(file.newInputStream())
        cloudFile.save() //thor exception if not exits
        success = cloudFile.exists()
        return success
    }


    void delete(Map params=[:]) {
        def opts = [:] << params
        def storageOptions = getAttachmentStorageOptions()
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
        return this.getCloudFile().exists() //org.apache.http.conn.ConnectTimeoutException: failed: connect timed out
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

    CloudFile getFile(typeName = ORIGINAL_STYLE) {
        return getCloudFile(typeName)
    }

    protected CloudFile getCloudFile(typeName = ORIGINAL_STYLE) {
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

    protected getAttachmentStorageOptions() {
        return RemoraUtil.storageOptions(this.parentEntity, this)
    }

    protected Map getAttachmentOptions() {
        String configClassName = this.isCopied ?
                this.domainClass : this.parentEntity.getClass().name
        def opts = Remora.registeredMapping(configClassName)
        def fieldName = this.isCopied ? this.propertyName : this.parentPropertyName
        return opts?."${fieldName}" ?: [:]
    }

    protected Map getAttachmentParentOptions() {
        String configClassName = this.parentEntity.getClass().name
        def opts = Remora.registeredMapping(configClassName)
        return opts?."${this.parentPropertyName}" ?: [:]
    }

    protected String evaluatedPath(String input, type = ORIGINAL_STYLE) {
        RemoraUtil.evaluatePath(input, parentEntity, this, type)
    }

    protected Class getParentEntityClass() {
        return RemoraUtil.getEntityClass(this.parentEntity)
    }
}
