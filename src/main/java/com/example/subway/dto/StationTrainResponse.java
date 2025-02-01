package com.example.subway.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StationTrainResponse {
    private String trainNo;
    private String upDown;
    private boolean lastTrain;         // 막차 여부 (true: 막차, false: 아님)
    private String currentStation;
    private String endStation;
    private int remainingStations;     // 남은 정거장 수
    private String lineNum;            // 열차가 운행 중인 호선 정보
}
