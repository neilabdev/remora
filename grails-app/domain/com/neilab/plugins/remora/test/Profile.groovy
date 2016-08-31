package com.neilab.plugins.remora.test

import com.neilab.plugins.remora.Attachment
import com.neilab.plugins.remora.AttachmentUserType

class Profile {
    String name
    String fileName
    Attachment file

    static hasMany = [uploads: Upload]

    static remora = [
            file: [
                    styles: [
                        // original:[width: 1024, mode:'fit'],
                        thumb: [width: 50, height: 50, mode: 'fit', format:'png'], //changes to format: 'png'
                        medium: [width: 250, height: 250, mode: 'scale'],
                        missing_width: [ height: 250, mode: 'scale'], //will be ignored
                        missing_height: [width: 250,   mode: 'scale'],//will be ignored
                        missing_mode: [width: 250, height: 250],//will be ignored
                    ],
                    assign:[originalFilename:'fileName',fileSize:'size'],
                    storage: [
                            url: "/:id/:type/:style/:propertyName/",
                            path: "attachment/:domainName/:propertyName/:id"
                    ]
            ],

            storage: [
                bucket:'default'
            ]
    ]


    def beforeValidate() { }

    static constraints = {
        file nullable:true
        fileName nullable:true
    }
    static mapping = {
        file type: AttachmentUserType
        uploads cascade: "all-delete-orphan"
    }

}
