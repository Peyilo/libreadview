package org.peyilo.readview.data

import android.os.Parcel
import android.os.Parcelable
import org.peyilo.libreadview.data.AdditionalData

/**
 * Parcelable Demo
 */
class User (
    val name: String,
    val age: Int,
    val address: String
): Parcelable, AdditionalData() {

    override fun describeContents(): Int  = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = with(dest) {
        writeString(name)
        writeInt(age)
        writeString(address)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<User> {
            override fun createFromParcel(source: Parcel): User = with(source) {
                return User(
                    readString()!!,
                    readInt(),
                    readString()!!
                )
            }

            override fun newArray(size: Int): Array<User?>? = arrayOfNulls(size)
        }
    }

}