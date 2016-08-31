package com.neilab.plugins.remora
import com.bertramlabs.plugins.karman.*
import com.bertramlabs.plugins.karman.local.*
import grails.test.mixin.integration.Integration
import grails.transaction.Rollback
import spock.lang.Specification

@Integration
@Rollback
class LocalProviderSpec extends Specification {
    StorageProvider provider

    def setup() {
        provider = StorageProvider.create(
                provider: 'local',
                basePath: "." ///path/to/storage/location
        )
    }

    def cleanup() {}

    void "Provider should save file"() {
        when: "Assigning profile"
          def bucket_name = "random_bucket_name"
          def directory = provider[bucket_name]
          def file = directory['test_dir/test_file.txt']
        then:
          directory instanceof Directory
          file instanceof LocalCloudFile
          directory != null
          file.exists() == false
        when: "updating file"
          file.text = "hello world"
          file = provider[bucket_name]['test_dir/test_file.txt'] as CloudFile
        then:
          file.contentLength > 0
          file.bytes.length == "hello world".length()

          directory.exists() == true
          file.exists() == true
          directory.listFiles(prefix: "test_dir").size() == 1
        when: "deleting file"
          file = provider[bucket_name]['test_dir/test_file.txt'] as CloudFile
            file.delete()
        then:
          provider[bucket_name]['test_dir/test_file.txt'].exists() == false
    }
}
