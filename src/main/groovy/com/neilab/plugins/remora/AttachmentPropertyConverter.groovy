package com.neilab.plugins.remora

import java.beans.PropertyEditorSupport

/***
 *  For use in marshalling/unmarshalling when using elastic search. e.g.
 *
 *  static searchable {
 *      attachment converter: AttachmentPropertyConverter, index: "not_analyzed"
 *  }
 */

class AttachmentPropertyConverter extends PropertyEditorSupport {
        @Override
        void setAsText(String text) throws IllegalArgumentException {
            Attachment attachment = text != null ? new Attachment(text) : null
            setValue(attachment)
        }

        @Override
        String getAsText() {
            Attachment attachment = (Attachment) this.getValue()
            String text = attachment?.toJson()
            return text
        }
}
