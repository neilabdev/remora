package com.neilab.plugins.remora.test

import com.neilab.plugins.remora.Attachment

class Upload {
    String name
    String fileName
    Long size
    Attachment file

    static belongsTo = Profile
    static constraints = {
        name nullable: false
        size nullable: true
        fileName nullable:  true
        file nullable: false
    }


    static remora = [
            file: [
                    assign:[originalFilename:'fileName',size:'size'],
                    styles: [
                            thumb: [width: 50, height: 50, mode: 'fit',format:'png'],
                            medium: [width: 250, height: 250, mode: 'scale']
                    ],
                    storage: [
                            url: "/:id/:type/:style/:propertyName/",
                            path: "attachment/:domainName/:propertyName/:id"
                    ],
            ],
            storage: [
                    bucket:'default'
            ]
    ]
}
