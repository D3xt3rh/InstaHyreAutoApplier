package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class JobDTO {
    private String id; // Opportunity ID from API
    private String title;
    private String company;
    private List<String> skills;
    private boolean applied; // Track if application was successful
}
