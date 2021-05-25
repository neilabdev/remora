package com.neilab.plugins.remora

import com.bertramlabs.plugins.karman.CloudFile
import com.bertramlabs.plugins.karman.local.LocalStorageController
import com.bertramlabs.plugins.karman.local.LocalStorageProvider
import com.neilab.plugins.remora.util.RemoraUtil

class AttachmentController {

    def attachmentService

    def show() {
        def config = grailsApplication.config.remora // grailsApplication.config.remora
        def storagePath = config.server.directory ?: 'attachment'
        def enabled = config.server.containsKey('enabled')  ? config.server.enabled ?: false : false
        def proxy = config.server.containsKey('proxy')  ? config.server.proxy ?: false : false

        if(!storagePath) {
            log.error("Remora Local Storage Path Not Specified. Please specify in your application.groovy property: remora.server.directory")
            render status: 500
            return
        } else if(!enabled) {
            log.error("Remora Attachment not enabled. Please specify in your application.groovy property: remora.server.enabled = true")
            render status: 500
            return
        }

        def localProvider = new LocalStorageProvider(basePath: storagePath)
        def extension = extensionFromURI(request.forwardURI)
        def fileName = params.name ?: params.id
        def format = servletContext.getMimeType(fileName)
        def directoryName = params.directory ?: '.'
        def (path,name) = RemoraUtil.pathComponents(fileName)

        if(extension && !fileName.endsWith(".${extension}")) {
            fileName = fileName + ".${extension}"
        }

        // No reverse traversal!
        if(request.forwardURI.contains('../') || request.forwardURI.contains('..\\')) {
            render status: 402
            return
        }

        def localFile = localProvider[directoryName][fileName] as CloudFile
        def fileExists = localFile.exists()
        if(!fileExists && proxy) {
            def recoveredFile = false
            def defaultProvider = attachmentService.storageProvider

            if(defaultProvider && !(defaultProvider instanceof LocalStorageProvider)) {
                //transfer file locally if it exists
                //serve local file
                def remoteFile = defaultProvider[directoryName][fileName] as CloudFile
                if(remoteFile.exists()) {
                    ensurePathExists(storagePath,"${directoryName}/${path}")
                    byte[] remoteData = remoteFile.bytes

                    localFile = localProvider[directoryName][fileName]
                    localFile.bytes = remoteData
                    localFile.save()
                    remoteData = null
                    if(localFile.exists())
                        recoveredFile=true
                }
            }

            if(!recoveredFile){
                render status: 404
                return
            }
        }

        // TODO: Private File Restrictions

        response.characterEncoding = request.characterEncoding
        response.contentType = format

        if(!fileExists) {
            render status: 404
            return
        } else if(config.local.sendFileHeader) {
            response.setHeader(config.local.sendFileHeader, localFile.fsFile.canonicalPath)
        } else {
            response.outputStream << localFile.inputStream
            response.flushBuffer()
        }

    }

    private extensionFromURI(uri) {
        def uriComponents = uri.split("/")
        def lastUriComponent = uriComponents[uriComponents.length - 1]
        def extension
        if(lastUriComponent.lastIndexOf(".") >= 0) {
            extension = uri.substring(uri.lastIndexOf(".") + 1)
        }
        return extension
    }

    private ensurePathExists(String base,String path) {
        def parentFile = new File(base,path) // fsFile.getParentFile()
        if(!parentFile.exists()) {
            parentFile.mkdirs()
        }
    }
}
