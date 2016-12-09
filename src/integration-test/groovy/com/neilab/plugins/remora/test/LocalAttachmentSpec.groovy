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

    def cleanup() { }

    void "Attachment should save file in model"() {
        when: "Assigning profile"
          def resource = grailsResourceLocator.findResourceForURI("/images/test/liberty_lover.jpg")
          def file = resource.file as File
          def profile = new Profile(name:"profile name", file: new Attachment(resource.file))
        then:
          profile.save() != null
        when:
          def attachmentInfo = attachmentService[profile]
          profile = profile.refresh() //Profile.findById(profile.id)

          def prefix =  profile.file.prefix
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
          provider['default'].listFiles(prefix:"${prefix}/" as String).size() == 3
        when: "Deleting model"
          profile.delete(failOnError: true,flush:true)
        then:
          provider['default'].listFiles(prefix:"${prefix}/" as String).size() == 0
    }
}
