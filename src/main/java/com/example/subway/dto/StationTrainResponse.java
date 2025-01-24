package com.example.subway.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StationTrainResponse {
    private String trainNo;
    private String upDown;
    private String expressYn;
    private String currentStation;
    private String endStation;
    private int remainingStations; // 남은 정거장 수
}
