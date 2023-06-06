package com.example.qrcodeins_java;

public class CheckpointDirections {

    private String targetDestination = "";

    public CheckpointDirections(String td) {
        this.targetDestination = td;
        setupDirections();
    }

    private void setupDirections() {
        String[] studyAreaDirections = new String[]{
                "Please keep going forward."
        };

        String[] multimediaLabDirections = new String[]{
                "Please keep going forward.",
                "Please keep moving forward.",
        };

        String[] ricohAreaDirections = new String[]{
                "Please keep going forward.",
                "Please keep moving forward.",
        };
    }
}