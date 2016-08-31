package com.neilab.plugins.remora.test

import com.neilab.plugins.remora.Attachment

class Upload {
    String name
    Long size
    Attachment file

    static belongsTo = Profile
    static constraints = {
        name nullable: false
    }


    static remora = [
            styles: [
                    thumb: [width: 50, height: 50, mode: 'fit'],
                    medium: [width: 250, height: 250, mode: 'scale']
            ],
            assign:[originalFilename:'name',fileSize:'size']
    ]
}
