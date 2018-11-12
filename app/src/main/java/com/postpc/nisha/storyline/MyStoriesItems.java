package com.postpc.nisha.storyline;

public class MyStoriesItems{

    private String storyName;
    private String endDate;

    MyStoriesItems(String name, String date){
        storyName = name;
        endDate = date;
    }

    public String getStoryName() {
        return storyName;
    }

    public String getEndDate() {
        return endDate;
    }
}