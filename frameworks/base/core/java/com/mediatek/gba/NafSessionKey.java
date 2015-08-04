package com.mediatek.gba;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Utitity contain class for NafSessionKey.
 * @hide
 */
public class NafSessionKey implements Parcelable {
    private String mBtid;
    private byte[] mKey;
    private String mKeylifetime;
    private String mNafKeyName;

    public NafSessionKey() {
        super();

    }

    public NafSessionKey(final String btid, final byte[] key, final String keylifetime) {
        mBtid = btid;
        mKey = key;
        mKeylifetime = keylifetime;
    }

    public String getBtid() {
        return mBtid;
    }

    public void setBtid(final String btid) {
        mBtid = btid;
    }

    public byte[] getKey() {
        return mKey;
    }

    public void setKey(final byte[] key) {
        mKey = key;
    }

    public String getKeylifetime() {
        return mKeylifetime;
    }

    public void setKeylifetime(final String keylifetime) {
        mKeylifetime = keylifetime;
    }

    public String getNafKeyName() {
        return mNafKeyName;
    }

    public void setNafKeyName(String nafKeyName) {
        mNafKeyName = nafKeyName;
    }

    /**
     * Implement the Parcelable interface
     * @hide
     */
    public int describeContents() {
        return 0;
    }

    /**
     * Implement the Parcelable interface.
     *
     * @param dest The Parcel in which the object should be written.
     * @param flags Additional flags about how the object should be written.
     * @hide
     */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mBtid);
        dest.writeByteArray(mKey);
        dest.writeString(mKeylifetime);
        dest.writeString(mNafKeyName);
    }

    /**
     * Implement the Parcelable interface.
     * @hide
     */
    public static final Creator<NafSessionKey> CREATOR =
        new Creator<NafSessionKey>() {
            public NafSessionKey createFromParcel(Parcel in) {
                NafSessionKey nafSessionKey = new NafSessionKey();
                String btid = in.readString();

                if (btid != null) {
                    nafSessionKey.setBtid(btid);
                }

                byte[] key = in.createByteArray();

                if (key != null) {
                    nafSessionKey.setKey(key);
                }

                String keylifetime = in.readString();

                if (keylifetime != null) {
                    nafSessionKey.setKeylifetime(keylifetime);
                }

                String nafKeyName = in.readString();

                if (nafKeyName != null) {
                    nafSessionKey.setNafKeyName(nafKeyName);
                }

                return nafSessionKey;
            }

            public NafSessionKey[] newArray(int size) {
                return new NafSessionKey[size];
            }
        };

    @Override
    public String toString() {
        synchronized (this) {
            StringBuilder builder = new StringBuilder("NafSessionKey: btid:");
            builder.append(mBtid).append(":").append(mKeylifetime).append(":").append(mNafKeyName);
            return builder.toString();
        }
    }
}