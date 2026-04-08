package com.example.orderapi.dto;

import java.util.List;

public class StructuredIntakeData {
    private String type;
    private String title;
    private String description;
    private String stepsToReproduce;
    private String expectedBehavior;
    private String priority;
    private List<String> affectedComponents;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStepsToReproduce() {
        return stepsToReproduce;
    }

    public void setStepsToReproduce(String stepsToReproduce) {
        this.stepsToReproduce = stepsToReproduce;
    }

    public String getExpectedBehavior() {
        return expectedBehavior;
    }

    public void setExpectedBehavior(String expectedBehavior) {
        this.expectedBehavior = expectedBehavior;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public List<String> getAffectedComponents() {
        return affectedComponents;
    }

    public void setAffectedComponents(List<String> affectedComponents) {
        this.affectedComponents = affectedComponents;
    }
}
