package com.neilab.plugins.remora
import com.neilab.plugins.remora.*
import com.neilab.plugins.remora.Attachment
import org.grails.datastore.gorm.validation.constraints.AbstractConstraint
import org.springframework.context.MessageSource
import org.springframework.validation.Errors
//import grails.validation.AbstractConstraint
/**
 * Created by ghost on 7/27/15.
 */
class ContentTypeConstraint extends AbstractConstraint {

    ContentTypeConstraint(Class<?> constraintOwningClass, String constraintPropertyName, Object constraintParameter, MessageSource messageSource) {
        super(constraintOwningClass, constraintPropertyName, constraintParameter, messageSource) //TODO: not sure what this is for? Grails 4.0
    }

    boolean supports(Class classObject) {
        classObject == Attachment
    }

    String getName() { "contentType" }

    protected void processValidate(target, propertyValue, Errors errors) {
        def contentType = propertyValue.contentType
        if (constraintParameter instanceof List) {
            if (!constraintParameter.contains(contentType)) {
                rejectValue target, errors, "default.invalid.${name}.message", "${name}.invalid", [constraintPropertyName, constraintOwningClass, contentType] as Object[]
            }
        } else if (constraintParameter != contentType) {
            rejectValue target, errors, "default.invalid.${name}.message", "${name}.invalid", [constraintPropertyName, constraintOwningClass, contentType] as Object[]
        }
    }

    protected boolean	skipBlankValues() { true }

    @Override  //TODO: not sure what this is for? Grails 4.0
    protected Object validateParameter(Object constraintParameter) {
        return null
    }

    protected boolean	skipNullValues() { true }
}
