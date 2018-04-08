package com.neilab.plugins.remora.test

import com.neilab.plugins.remora.Attachment
import com.neilab.plugins.remora.AttachmentService
import grails.test.mixin.integration.Integration
import com.bertramlabs.plugins.karman.StorageProvider
import com.neilab.plugins.remora.test.Profile

//import org.springframework.com.neilab.plugins.remora.test.annotation.Rollback
import grails.transaction.*

/**
 * Created by ghost on 7/29/15.
 */
import spock.lang.Specification

/**
 * Created by ghost on 8/1/15.
 */
@Integration
@Rollback
class LocalAttachmentSpec extends Specification {


    StorageProvider provider
    def grailsResourceLocator
    def grailsApplication
    AttachmentService attachmentService

    def setup() {
        /* provider = StorageProvider.create(
                 provider: 'database' //,
                 //    basePath: "/path/to/storage/location"
         ) */

        grailsResourceLocator = grailsApplication.mainContext.getBean('grailsResourceLocator')
        attachmentService = grailsApplication.mainContext.getBean('attachmentService')
        provider = attachmentService.storageProvider
    }

    def cleanup() {}

    void "Attachment should save file in model"() {
        when: "Assigning profile"
            def resource = grailsResourceLocator.findResourceForURI("/images/test/liberty_lover.jpg")

            def file = resource.file as File
            def profile = new Profile(name: "profile name", file: new Attachment(resource.file))
        then:
            profile.save() != null
        when:
            def attachmentInfo = attachmentService[profile]
            profile = profile.refresh() //Profile.findById(profile.id)

            def prefix = profile.file.prefix
        then:
            profile != null
            attachmentInfo.file != null
            profile.file.name == "liberty_lover.jpg"
            profile.file.originalFilename == "liberty_lover.jpg"
            profile.file.size == 388935
            profile.file.contentType == "image/jpeg"
            profile.file.domainName == "profile"
            profile.file.propertyName == "file"
            profile.fileName == profile.file.name
            profile.file.url != null
            profile.file.cloudFile.contentLength == file.size()
            profile.file.getCloudFile("thumb").name.endsWith(".png") == true
            provider['default'].listFiles(prefix: "${prefix}/" as String).size() == 3
        when: "Deleting model"
            profile.delete(failOnError: true, flush: true)
        then:
            provider['default'].listFiles(prefix: "${prefix}/" as String).size() == 0
    }


    void "Attachment should save and copy file via relational model "() {
        given:
            def resource = [
                    file1: grailsResourceLocator.findResourceForURI("/images/test/liberty_lover.jpg").file,
                    file2: grailsResourceLocator.findResourceForURI("/images/test/liberty_lover.jpg").file,
                    file3: grailsResourceLocator.findResourceForURI("/images/test/liberty_lover.jpg").file,
                    file4: grailsResourceLocator.findResourceForURI("/images/test/liberty_lover.jpg").file
            ]
            def profile = new Profile(name: "profile name", file: new Attachment(resource.file1))
        when: "Assigning profile"
            profile.addToUploads(name: "upload1", file: resource.file2)
            profile.addToUploads(name: "upload2", file: resource.file3)
            profile.addToUploads(name: "upload3", file: resource.file4)
        then:
            profile.save(flush: true) != null
        when:
            def attachmentInfo = attachmentService[profile]
            def uploads = profile.refresh().uploads
            def prefix = profile.file.prefix
            def uploads_prefix = [
                    file1: uploads[0].file.prefix,
                    file2: uploads[1].file.prefix,
                    file3: uploads[2].file.prefix
            ]
        then:
            uploads.size() == 3
            uploads[0].size == 388935
            uploads[0].file.isPersisted == true
          //  uploads[0].file.isReadOnly == true

            uploads[1].size == 388935
            uploads[1].file.isPersisted == true

            uploads[2].size == 388935
            uploads[2].file.isPersisted == true
        when:
            profile.preview1 = uploads[0].file.copy
            profile.preview2 = uploads[1].file.copy
            profile.preview3 = uploads[2].file.copy
            profile.save(flush:true)

        then:
            profile.hasErrors() == false
            profile.refresh().preview1.url == uploads[0].file.url
            profile.preview2.url == uploads[1].file.url
            profile.preview3.url == uploads[2].file.url
            attachmentInfo.file != null
            profile.file.name == "liberty_lover.jpg"
            profile.file.originalFilename == "liberty_lover.jpg"
            profile.file.size == 388935
            profile.file.contentType == "image/jpeg"
            profile.file.domainName == "profile"
            profile.file.propertyName == "file"
            profile.fileName == profile.file.name
            profile.file.url != null
            profile.file.cloudFile.contentLength == resource.file1.size()
        and:
            profile.preview1.name == "liberty_lover.jpg"
            profile.preview1.originalFilename == "liberty_lover.jpg"
            profile.preview1.size == 388935
            profile.preview1.contentType == "image/jpeg"
            profile.preview1.domainName == "upload"
            profile.preview1.propertyName == "file"
            profile.preview1.url != null
            profile.preview1.cloudFile.contentLength == resource.file2.size()
            uploads[0].file.getCloudFile("thumb").name.endsWith(".png") == true
            profile.preview1.getCloudFile("thumb").name.endsWith(".png") == true
            provider['default'].listFiles(prefix: "${prefix}/" as String).size() == 3
        //uploads_prefix
            provider['default'].listFiles(prefix: "${uploads_prefix.file1}/" as String).size() == 3
            provider['default'].listFiles(prefix: "${uploads_prefix.file2}/" as String).size() == 3
            provider['default'].listFiles(prefix: "${uploads_prefix.file3}/" as String).size() == 3
        when: "Deleting model"
            profile.delete(failOnError: true, flush: true)
        then:
            provider['default'].listFiles(prefix: "${prefix}/" as String).size() == 0
            provider['default'].listFiles(prefix: "${uploads_prefix.file1}/" as String).size() == 0
            provider['default'].listFiles(prefix: "${uploads_prefix.file2}/" as String).size() == 0
            provider['default'].listFiles(prefix: "${uploads_prefix.file3}/" as String).size() == 0
    }
}
