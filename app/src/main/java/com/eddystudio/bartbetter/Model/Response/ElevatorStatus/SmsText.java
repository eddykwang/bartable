
package com.eddystudio.bartbetter.Model.Response.ElevatorStatus;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class SmsText {

    @SerializedName("#cdata-section")
    @Expose
    private String cdataSection;

    public String getCdataSection() {
        return cdataSection;
    }

    public void setCdataSection(String cdataSection) {
        this.cdataSection = cdataSection;
    }

}
