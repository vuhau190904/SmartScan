package thesis.android.smart_scan.converter

import android.net.Uri
import io.objectbox.converter.PropertyConverter
import androidx.core.net.toUri

class UriConverter : PropertyConverter<Uri?, String?> {
    override fun convertToEntityProperty(databaseValue: String?): Uri? {
        return databaseValue?.toUri()
    }

    override fun convertToDatabaseValue(entityProperty: Uri?): String? {
        return entityProperty?.toString()
    }
}