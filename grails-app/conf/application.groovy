import com.neilab.plugins.remora.Attachment
import com.neilab.plugins.remora.AttachmentUserType

grails.gorm.default.mapping = {
    "user-type" type: AttachmentUserType, class: Attachment
}