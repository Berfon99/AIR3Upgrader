package com.xc.air3upgrader

import android.os.Parcel
import android.os.Parcelable

data class AppInfo(
    var name: String,
    val `package`: String,
    var latestVersion: String,
    var apkPath: String,
    var installedVersion: String? = null,
    val compatibleModels: List<String>,
    val minAndroidVersion: String,
    var isSelectedForUpgrade: Boolean = false,
    var highestServerVersion: String = "",
    var air3ManagerOriginalFileName: String? = null // Added this line
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString(),
        parcel.createStringArrayList() ?: emptyList(),
        parcel.readString() ?: "",
        parcel.readByte() != 0.toByte(),
        parcel.readString() ?: "",
        parcel.readString() // Added this line
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(`package`)
        parcel.writeString(latestVersion)
        parcel.writeString(apkPath)
        parcel.writeString(installedVersion)
        parcel.writeStringList(compatibleModels)
        parcel.writeString(minAndroidVersion)
        parcel.writeByte(if (isSelectedForUpgrade) 1 else 0)
        parcel.writeString(highestServerVersion)
        parcel.writeString(air3ManagerOriginalFileName) // Added this line
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<AppInfo> {
        override fun createFromParcel(parcel: Parcel): AppInfo {
            return AppInfo(parcel)
        }

        override fun newArray(size: Int): Array<AppInfo?> {
            return arrayOfNulls(size)
        }
    }
    override fun toString(): String {
        return "AppInfo(name='$name', package='$`package`', latestVersion='$latestVersion', apkPath='$apkPath', installedVersion='$installedVersion', compatibleModels=$compatibleModels, minAndroidVersion='$minAndroidVersion', isSelectedForUpgrade=$isSelectedForUpgrade, highestServerVersion='$highestServerVersion', air3ManagerOriginalFileName=$air3ManagerOriginalFileName)" // Added this line
    }
}

data class AppsData(
    val apps: List<AppInfo>
)