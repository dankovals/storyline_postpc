package com.postpc.nisha.storyline;

import com.postpc.nisha.storyline.classifier.Result;

public class ClassifiedImage {
    private String path;
    private Result classificationResult;


    public ClassifiedImage(String path, Result classificationResult){
        this.path = path;
        this.classificationResult = classificationResult;
    }

    public String getPath() {
        return path;
    }

    public Result getClassificationResult() {
        return classificationResult;
    }

}
