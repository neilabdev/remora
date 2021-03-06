package com.neilab.plugins.remora

import com.neilab.plugins.remora.Attachment
import grails.databinding.converters.ValueConverter
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.commons.CommonsMultipartFile

/**
 * Created by ghost on 7/27/15.
 */
class AttachmentValueConverter  implements ValueConverter {


    boolean canConvert(value) {
        [CommonsMultipartFile, MultipartFile, String, InputStream].collect { it.isInstance(value) }.find { it } //== true
    }
    def convert(value) {

        if(value instanceof MultipartFile) {
            if (!value.originalFilename) {
                return null
            }
         return new Attachment(contentType: value.contentType,name: value.name, originalFilename: value.originalFilename,
                    size: value.size, inputStream: value.inputStream)
        } else if (value instanceof String) {
            return new Attachment(value as String)
        } else if (value instanceof InputStream) {
            return new Attachment(value as InputStream)
        }
    }

    Class<?> getTargetType() { Attachment }
}
