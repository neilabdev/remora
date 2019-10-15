package com.neilab.plugins.remora.processors

import com.neilab.plugins.remora.Attachment
import org.imgscalr.Scalr
import groovy.util.logging.Log4j

import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import javax.imageio.stream.FileImageOutputStream
import java.awt.image.BufferedImage
import java.awt.image.BufferedImageOp

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
        def formatName = formatNameFromContentType(attachment.contentType)
        //TODO: if no contentType exists, this fails silently and return on #28
        def styleOptions = (attachment.options?.styles  ?: [:] ) as Map<String,Map>
        def defaultOptions = (attachment.options  ?: [:] ).findAll{ ['filter','mode'].contains(it.key)}
        BufferedImage image = null
        def success = true

        if (!formatName || !styleOptions) {
            return success
        }

        styleOptions.each {  String styleName, styleValue ->
            Map style = [format: formatName] + (styleValue?.clone() ?: [:])
            if (success && validStyle(attachment, styleName, style)) {
                image = image ?: ImageIO.read(attachment.inputStream)
                if (!processStyle(styleName, style,defaultOptions, image)) { //todo: should finish processing if one image fails?
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

        if (!['fit', 'crop', 'scale'].contains(style.mode)) {
            log.error("Style :${styleName} for property :${validateAttachment.propertyName} on model :${validateAttachment.domainName} requires a :mode with values ['fit','crop','scale'] but received '${style.mode}' ")
            success = false
        }

        if (enable_fit) {
            if (!has_width && !has_height) {
                log.error("Style :${styleName} for property :${validateAttachment.propertyName} on model :${validateAttachment.domainName} requires that :width or :height is a integer and is > 0 but received w: '${style.width}' h: '${style.height}' ")
                success = false
            }
        } else {
            if (!style.width instanceof Integer || style.width < 1) {
                log.error("Style :${styleName} for property :${validateAttachment.propertyName} on model :${validateAttachment.domainName} requires a :width that is a integer and is > 0 but received '${style.width}' ")
                success = false
            }

            if (!style.height instanceof Integer || style.height < 1) {
                log.error("Style :${styleName} for property :${validateAttachment.propertyName} on model :${validateAttachment.domainName} requires a :height that is a integer and is > 0 but received '${style.height}' ")
                success = false
            }
        }

        if (!success)
            log.error("Skipping style :${styleName} for property :${validateAttachment.propertyName} on model :${validateAttachment.domainName} for the prior errors")

        return success
    }

    protected def processStyle(typeName, Map options, Map default_options=[:], BufferedImage image) {
        boolean success = false
        String format = options.format as String
        File tempFile = File.createTempFile("remora-", "-${format}") //TODO: Add config for temp ath
        int options_width = (options.width ?: image.width) as Integer
        int options_height = (options.height ?: image.height) as Integer
        BufferedImageOp[] opts = (options.filter ?: default_options.filter ?: [Scalr.OP_ANTIALIAS]).collect { o ->
            o instanceof String ?
                    [
                            'antialias': Scalr.OP_ANTIALIAS,
                            'brighter' : Scalr.OP_BRIGHTER,
                            'darker'   : Scalr.OP_DARKER,
                            'grayscale': Scalr.OP_GRAYSCALE,
                            'none': null
                    ].get((String) o.toLowerCase()) : o
        }.find { o -> o instanceof BufferedImageOp } as BufferedImageOp[]

        String options_mode = options.mode ?: default_options.mode

        try {
            BufferedImage outputImage = image

            if (options_mode == 'fit') {

                def mode = Scalr.Mode.FIT_TO_HEIGHT
                if (image.width < image.height) {
                    mode = Scalr.Mode.FIT_TO_WIDTH
                }
                def xOffset = 0
                def yOffset = 0
                def should_crop = options.width && options.height


                if (should_crop) {
                    outputImage = Scalr.resize(image, Scalr.Method.ULTRA_QUALITY, mode, options_width, options_height, opts)
                } else if (options.width) {
                    outputImage = Scalr.resize(image, Scalr.Mode.FIT_TO_WIDTH, options_width, opts)
                    //only width supplie
                } else if (options.height) {
                    //only height supplied
                    outputImage = Scalr.resize(image, Scalr.Mode.FIT_TO_HEIGHT, options_height, opts)
                }

                if (!options.x) {
                    xOffset = Math.floor((outputImage.width - options_width) / 2).toInteger()
                }

                if (!options.y) {
                    yOffset = Math.floor((outputImage.height - options_height) / 2).toInteger()
                }

                if (should_crop)
                    outputImage = Scalr.crop(outputImage, xOffset, yOffset, options_width, options_height, opts)
            } else if (options_mode == 'crop') {
                outputImage = Scalr.crop(outputImage, (options.x ?: 0) as Integer, (options.y ?: 0) as Integer, options_width, options_height, Scalr.OP_ANTIALIAS)
            } else if (options_mode == 'scale') {
                outputImage = Scalr.resize(image, Scalr.Method.ULTRA_QUALITY, Scalr.Mode.AUTOMATIC,
                        options_width, options_height, opts)
            } else {
                outputImage = image
            }

            if (["jpeg"].contains(options.format)) {
                ImageWriter jpgWriter = ImageIO.getImageWritersByFormatName("jpg").next()
                ImageWriteParam jpgWriteParam = jpgWriter.getDefaultWriteParam()
                jpgWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
                jpgWriteParam.setCompressionQuality(1.0f)
                jpgWriter.setOutput(new FileImageOutputStream(tempFile))
                IIOImage savedOutputImage = new IIOImage(outputImage, null, null)
                jpgWriter.write(null, savedOutputImage, jpgWriteParam)
                jpgWriter.dispose()

                success = attachment.saveProcessedStyle(typeName, tempFile,
                        width: outputImage.width, height: outputImage.height, style: typeName)
            } else {

                //def saveStream = new ByteArrayOutputStream()
                ImageIO.write(outputImage, options.format as String, tempFile)
                success = attachment.saveProcessedStyle(typeName, tempFile,
                        width: outputImage.width, height: outputImage.height, style: typeName)
            }

        } catch (e) {
            log.error("Error Processing Uploaded File ${attachment.name} - ${typeName}", e)
        } finally {
            if (!tempFile.delete()) //should never return false
                tempFile.deleteOnExit()
        }
        return success
    }

    def formatNameFromContentType(contentType) {
        CONTENT_TYPE_TO_NAME[contentType]
    }

}