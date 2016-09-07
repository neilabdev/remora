Remora
======

Remora is a Grails Image / File Upload / Attachment Plugin. It was initially based on the excellent Selfie plugin by bertramlabs, which at the time was limited to grails 2 and implemented using grails column embedding feature to incorporate attachments into the domain model, which was not ideal for my specific project and thus a fork was created to resolve these issues with additional features. 

You may use Remora to attach files to your domain models, upload to a CDN, validate content, and produce thumbnails.

* Domain Attachment
* CDN Storage Providers (via Karman)
* Image Resizing (imgscalr)
* Content Type Validation
* GORM Bindings / Hibernate User Types Support

Installation
------------

Add The Following to your `build.gradle`:

```groovy
dependencies {
    compile ':remora:1.0.0'
}
```

Configuration
-------------

Remora utilizes karman for dealing with asset storage. Karman is a standardized interface for sending files up to CDN's as well as local file stores. It is also capable of serving local files.
In order to upload files, we must first designate a storage provider for these files. This can be done in the `remora` static map in each GORM domain with which you have an Attachment,
or this can be defined in your `application.groovy` or 'application.yml'.

```groovy
remora {
    storage {
        bucket = 'uploads'
        providerOptions {
            provider = 'local' // // Switch to s3 if you wish to use s3 and install the karman-aws plugin
            basePath = 'storage'
            baseUrl = 'http://localhost:8080/image-test/storage'
        }

        provider {
            local {
                basePath = 'storage'
                baseUrl = 'http://localhost:8080/image-test/storage'
            }

            s3 {
                basePath = 'storage'
                baseUrl = 'http://localhost:8080/image-test/storage'
                //accessKey = "KEY" //Used for S3 Provider
                //secretKey = "KEY" //Used for S3 Provider
            }
        }
    }
}

```

The `providerOptions` section will pass straight through to karmans `StorageProvider.create()` factory. The `provider` specifies the storage provider to use while the other options are specific to each provider.

In the above example we are using the karman local storage provider. This is all well and good, but we also need to be able to serve these files from a URL. Depending on your environment this can get a bit tricky.
One option is to use nginx to serve the directory and point the `baseUrl` to the appropriate endpoint. Another option is to use the built in endpoint provided by the karman plugin:

**NOTE**:

You can also configure which bucket or karman storage provider is used on a per domain level as well as per property level basis in your application config. For example the `Book` domain class could be configured as follows:

```groovy

remora {
    domain {
        book {
            storage {
                path = 'uploads/:class/:id/:propertyName/' //This configures the storage path of the files being uploaded by domain class name and property name and identifier in GORM
                bucket = 'uploads'
                providerOptions {
                    provider = 'local' // Switch to s3 if you wish to use s3 and install the karman-aws plugin
                    basePath = 'storage'
                    baseUrl  = 'http://localhost:8080/image-test/storage'
                    //accessKey = "KEY" //Used for S3 Provider
                    //secretKey = "KEY" //Used for S3 Provider
                }
            }
        }
    }
}
  

```


Usage
-----

Unlike its worthy predecessor Selfie, the Remora plugin does not use an embedded GORM domain class to provide an elegant DSL for uploading and attaching files to your domains. You merely only need to add the Attachment class as the type for your storage file. Instead of embedding, aforesaid type becomes a single text field column which is serialized to JSON, instead of multiple columns that would be added if it were embedded.

Example DSL:

```groovy
import com.neilab.plugins.remora.Attachment
import com.neilab.plugins.remora.AttachmentUserType

class Book {
  String name
  Integer size
  Attachment photo      // only this is require, everything else is optional

  static remora = [
    photo: [    // if a key is the name of a property and  not reserved (see below), can be used to configure attachments properties
      styles: [
        thumb: [width: 50, height: 50, mode: 'fit'],
        medium: [width: 250, height: 250, mode: 'scale']
      ]
    ],
    
    storage: [ // reserved key which allows overriding application configs for this domain.
        bucket:'book_bucket',
        //:url & :path are interpolated using variables :class,:domainName,:style,:propertyName,:id and :type
        url: "/:id/:type/:style/:propertyName/",  
        path: "attachment/:domainName/:propertyName/:id"
    ],
    
    assign: [ // reserved key which allows you to assign the attachment properties to additional model database columns.
        originalFilename:'name', // assigns filename of original attachment to property :name
        fileSize:'size' // assigns size of original attachment to property :size
    ]
  ]
   
  static constraints = {
    photo contentType: [‘png’,’jpg’], fileSize:1024*1024 // 1mb
  }
}
```

Uploading Files could not be simpler. Simply use a multipart form and upload a file:

```gsp
<g:uploadForm name="upload" url="[action:'upload',controller:'photo']">
  <input type="file" name="photo" /><br/>
  <g:submitButton name="update" value="Update" /><br/>
</g:uploadForm>
```

When you bind your params object to your GORM model, the file will automatically be uploaded upon save and processed:

```groovy
class PhotoController {
  def upload() {
    def photo = new Photo(params)
    if(!photo.save()) {
      println "Error Saving! ${photo.errors.allErrors}"
    }
    redirect view: "index"
  }
}
```

Things to be Done
------------------
* Better documentation