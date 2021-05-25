package com.neilab.plugins.remora.processors

import com.neilab.plugins.remora.Attachment
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.imgscalr.Scalr
//import groovy.util.logging.Log4j
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


@Slf4j
//@CompileStatic
class ImageResizer {

    private static final Map<String, String> CONTENT_TYPE_TO_NAME = [
            'image/jpeg': 'jpeg', 'image/png': 'png', 'image/bmp': 'bmp', 'image/gif': 'gif'
    ]

    Attachment attachment

    boolean process() {
        def formatName = formatNameFromContentType(attachment.contentType)
        //TODO: if no contentType exists, this fails silently and return on #28
        def styleOptions = (attachment.options?.styles ?: [:]) as Map<String, Map>
        def defaultOptions = (attachment.options ?: [:]).findAll { ['filter', 'mode','quality'].contains(it.key) }
        BufferedImage image = null
        def success = true

        if (!formatName || !styleOptions) {
            return success
        }

        styleOptions.each { String styleName, def styleValue ->
             Map value  = styleValue instanceof Map ? (Map)styleValue : [:]
            Map style = [format: formatName] + value
            if (success && validStyle(attachment, styleName, style)) {
                image = image ?: ImageIO.read(attachment.inputStream)
                if (!processStyle(styleName, style, defaultOptions, image)) {
                    //todo: should finish processing if one image fails?
                    success = false
                }
            } else {
                return false
            }
        }
        return success
    }

    static boolean validStyle(Attachment validateAttachment, String styleName, Map style) {
        boolean success = true
        Integer style_width =  style.width instanceof Integer ? (Integer) style.width as Integer : null
        Integer style_height =  style.height instanceof Integer ? (Integer) style.height as Integer : null

        boolean has_width = style_width != null && style_width > 1
        boolean has_height = style_height != null && style_height > 1
        boolean enable_fit = ['fit'].contains(style.mode)
      //  boolean default_mode = style.mode ?: 'scale'

        if (!style.mode || !['fit', 'crop', 'scale'].contains(style.mode)) {
            log.error("Style :${styleName} for property :${validateAttachment.propertyName} on model :${validateAttachment.domainName} requires a :mode with values ['fit','crop','scale'] but received '${style.mode}' ")
            success = false
        } else if (enable_fit) {
            if (!has_width && !has_height) {
                log.error("Style :${styleName} for property :${validateAttachment.propertyName} on model :${validateAttachment.domainName} requires that :width or :height is a integer and is > 0 but received w: '${style.width}' h: '${style.height}' ")
                success = false
            }
        } else {
            if (!style_width || style_width < 1) {
                log.error("Style :${styleName} for property :${validateAttachment.propertyName} on model :${validateAttachment.domainName} requires a :width that is a integer and is > 0 but received '${style.width}' ")
                success = false
            }

            if (!style_height || style_height < 1) {
                log.error("Style :${styleName} for property :${validateAttachment.propertyName} on model :${validateAttachment.domainName} requires a :height that is a integer and is > 0 but received '${style.height}' ")
                success = false
            }
        }

        if (!success)
            log.error("Skipping style :${styleName} for property :${validateAttachment.propertyName} on model :${validateAttachment.domainName} for the prior errors")

        return success
    }

    private static List ensureArray(def obj) {
        List list = (!obj || obj instanceof List) ? obj as List  : [obj]
        return list ?: []
    }

    protected def processStyle(String typeName, Map options, Map default_options = [:], BufferedImage image) {
        boolean success = false
        String format = options.format as String
        File tempFile = File.createTempFile("remora-", "-${format}") //TODO: Add config for temp ath
        int options_width = (options.width ?: image.width) as Integer
        int options_height = (options.height ?: image.height) as Integer

        BufferedImageOp[] opts = (ensureArray(options.filter ?: default_options.filter) ?: [Scalr.OP_ANTIALIAS]).collect { o ->
            o instanceof String ?
                    [
                            'antialias': Scalr.OP_ANTIALIAS,
                            'brighter' : Scalr.OP_BRIGHTER,
                            'darker'   : Scalr.OP_DARKER,
                            'grayscale': Scalr.OP_GRAYSCALE,
                            'none'     : null
                    ].get((String) o.toLowerCase()) : o
        }.find { o -> o instanceof BufferedImageOp } as BufferedImageOp[]

        def option_quality = options.quality ?: default_options.quality ?: 'auto'
        Scalr.Method quality = (option_quality instanceof Scalr.Method) ? options.quality as Scalr.Method  :
                [
                        'ultra_high': Scalr.Method.ULTRA_QUALITY,
                        'maximum'      : Scalr.Method.ULTRA_QUALITY,
                        'high'   : Scalr.Method.QUALITY,
                        'quality'   : Scalr.Method.QUALITY,
                        'speed'     : Scalr.Method.SPEED,
                        'balanced'  : Scalr.Method.BALANCED,
                        'auto'      : Scalr.Method.AUTOMATIC
                ].get(option_quality) as Scalr.Method ?: Scalr.Method.AUTOMATIC

        String options_mode = options.mode ?: default_options.mode

        try {
            BufferedImage outputImage = image

            if (options_mode == 'fit') {

                def mode = Scalr.Mode.FIT_TO_HEIGHT
                if (image.width < image.height) {
                    mode = Scalr.Mode.FIT_TO_WIDTH
                }
                Integer xOffset = 0 as Integer
                Integer yOffset = 0 as Integer
                def should_crop = options.width && options.height


                if (should_crop) {
                    outputImage = Scalr.resize(image, quality, mode, options_width, options_height, opts)
                } else if (options.width) {
                    log.info("Resizing image to fit :width while applying filters: ${opts?.length ?: 0}")
                    outputImage = Scalr.resize(image, Scalr.Mode.FIT_TO_WIDTH, options_width, opts)
                    //only width supplie
                } else if (options.height) {
                    //only height supplied
                    outputImage = Scalr.resize(image, Scalr.Mode.FIT_TO_HEIGHT, options_height, opts)
                }

                if (!options.x) {
                     xOffset = Math.floor(((outputImage.width - options_width) / 2).toDouble()).toInteger()
                }

                if (!options.y) {
                    yOffset = Math.floor(((outputImage.height - options_height) / 2).toDouble()).toInteger()
                }

                if (should_crop)
                    outputImage = Scalr.crop(outputImage, xOffset, yOffset, options_width, options_height, opts)
            } else if (options_mode == 'crop') {
                outputImage = Scalr.crop(outputImage, (options.x ?: 0) as Integer, (options.y ?: 0) as Integer, options_width, options_height, Scalr.OP_ANTIALIAS)
            } else if (options_mode == 'scale') {
                outputImage = Scalr.resize(image, quality, Scalr.Mode.AUTOMATIC,
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

    static String formatNameFromContentType(String contentType) {
        return CONTENT_TYPE_TO_NAME[contentType]
    }

}