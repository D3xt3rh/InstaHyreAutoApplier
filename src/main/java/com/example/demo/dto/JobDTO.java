package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class JobDTO {
    private String title;
    private List<String> skills;
}
