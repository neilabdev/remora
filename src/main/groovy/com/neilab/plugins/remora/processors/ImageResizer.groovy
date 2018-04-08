package com.neilab.plugins.remora.processors
import com.neilab.plugins.remora.Attachment
import org.imgscalr.Scalr
import groovy.util.logging.Log4j
import javax.imageio.ImageIO
import java.awt.image.BufferedImage

/**
 * Created by ghost on 7/29/15.
 */


@Log4j
class ImageResizer {

    private static final Map<String, String> CONTENT_TYPE_TO_NAME = [
            'image/jpeg': 'jpeg', 'image/png': 'png', 'image/bmp': 'bmp', 'image/gif': 'gif'
    ]

    Attachment attachment

    boolean process() {
        def formatName = formatNameFromContentType(attachment.contentType) //TODO: if no contentType exists, this fails silently and return on #28
        def styleOptions = attachment.options?.styles
        def image = null
        def success = true

        if (!formatName || !styleOptions) {
            return success
        }

        styleOptions.each {styleName,styleValue->
            def style = [format: formatName] + (styleValue?.clone() ?: [:])
            if(success && validStyle(attachment,styleName,style)) {
                image = image ?: ImageIO.read( attachment.fileBytes ? new ByteArrayInputStream(attachment.fileBytes) : attachment.inputStream)
                if(!processStyle(styleName, style, image)) { //todo: should finish processing if one image fails?
                    success = false
                }
            }
        }
        return success
    }

    static boolean validStyle(Attachment validateAttachment, String styleName, def style) {
        boolean success = true
        boolean has_width = style.width instanceof Integer && style.width > 1
        boolean has_height = style.height instanceof Integer && style.height > 1
        boolean enable_fit = ['fit'].contains(style.mode)

        if(!['fit','crop','scale'].contains(style.mode)) {
            log.error("Style :${styleName} for property :${validateAttachment.propertyName} on model :${validateAttachment.domainName} requires a :mode with values ['fit','crop','scale'] but received '${style.mode}' ")
            success = false
        }

        if(enable_fit) {
            if(!has_width && !has_height) {
                log.error("Style :${styleName} for property :${validateAttachment.propertyName} on model :${validateAttachment.domainName} requires that :width or :height is a integer and is > 0 but received w: '${style.width}' h: '${style.height}' ")
                success = false
            }
        } else {
            if(!style.width instanceof Integer || style.width < 1) {
                log.error("Style :${styleName} for property :${validateAttachment.propertyName} on model :${validateAttachment.domainName} requires a :width that is a integer and is > 0 but received '${style.width}' ")
                success = false
            }

            if(!style.height instanceof Integer || style.height < 1) {
                log.error("Style :${styleName} for property :${validateAttachment.propertyName} on model :${validateAttachment.domainName} requires a :height that is a integer and is > 0 but received '${style.height}' ")
                success = false
            }
        }

        if(!success)
            log.error("Skipping style :${styleName} for property :${validateAttachment.propertyName} on model :${validateAttachment.domainName} for the prior errors")

        return success
    }

    protected def processStyle(typeName, options, BufferedImage image) {
        boolean success = false 
        try {
            BufferedImage outputImage

            if (options.mode == 'fit') {

                def mode = Scalr.Mode.FIT_TO_HEIGHT
                if (image.width < image.height) {
                    mode = Scalr.Mode.FIT_TO_WIDTH
                }
                def xOffset = 0
                def yOffset = 0
                def options_width = options.width ?: image.width as Integer
                def options_height = options.height ?: image.height as Integer
                def should_crop = options.width && options.height

                if(should_crop) {
                    outputImage = Scalr.resize(image, Scalr.Method.AUTOMATIC, mode, options_width, options_height, Scalr.OP_ANTIALIAS)
                } else if(options.width) {
                    outputImage = Scalr.resize(image,Scalr.Mode.FIT_TO_WIDTH,options_width,Scalr.OP_ANTIALIAS)
                    //only width supplie
                } else if(options.height) {
                    //only height supplied
                    outputImage = Scalr.resize(image,Scalr.Mode.FIT_TO_HEIGHT,options_height,Scalr.OP_ANTIALIAS)
                }

                if (!options.x) {
                    xOffset = Math.floor((outputImage.width - options_width) / 2).toInteger()
                }

                if (!options.y) {
                    yOffset = Math.floor((outputImage.height - options_height) / 2).toInteger()
                }

                if(should_crop)
                    outputImage = Scalr.crop(outputImage, xOffset, yOffset, options_width, options_height, Scalr.OP_ANTIALIAS)
            } else if (options.mode == 'crop') {
                outputImage = Scalr.crop(outputImage, options.x ?: 0, options.y ?: 0, options.width, options.height, Scalr.OP_ANTIALIAS)
            } else if (options.mode == 'scale') {
                outputImage = Scalr.resize(image, Scalr.Method.AUTOMATIC, Scalr.Mode.AUTOMATIC, options.width, options.height, Scalr.OP_ANTIALIAS)
            }

            def saveStream = new ByteArrayOutputStream()

            ImageIO.write(outputImage, options.format, saveStream)
            success = attachment.saveProcessedStyle(typeName, saveStream.toByteArray(),
                    width: outputImage.width,height: outputImage.height,style: typeName)
        } catch (e) {
            log.error("Error Processing Uploaded File ${attachment.name} - ${typeName}", e)
        }
        return success
    }

    def formatNameFromContentType(contentType) {
        CONTENT_TYPE_TO_NAME[contentType]
    }

}