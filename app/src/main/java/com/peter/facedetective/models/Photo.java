package com.peter.facedetective.models;

import android.graphics.Bitmap;

/**
 * Created by Peter on 12/27/16.
 * Represents a photo with all attributes and everything
 */

public class Photo {

    private boolean hasPhotoBitmap;         // has a photo

    private Bitmap imageBitmap;             // holds the image
    private boolean analysed;               // whether image has been analysed
    private String photoPath;               // path to photo on device

    public Photo() {
        imageBitmap = null;
        analysed = false;
        hasPhotoBitmap = false;
    }

    public boolean hasPhoto() {
        return hasPhotoBitmap;
    }

    public Bitmap getImageBitmap() {
        return imageBitmap;
    }

    public Photo setImageBitmap(Bitmap imageBitmap) {
        this.imageBitmap = imageBitmap;
        hasPhotoBitmap = true;
        return this;
    }

    public boolean isAnalysed() {
        return analysed;
    }

    public Photo setAnalysed(boolean analysed) {
        this.analysed = analysed;
        return this;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public Photo setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
        return this;
    }
}
