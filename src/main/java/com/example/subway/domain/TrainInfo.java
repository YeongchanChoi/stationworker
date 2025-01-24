package com.example.subway.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class TrainInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String trainNo;        // 열차번호
    private String upDown;         // 상하선 정보
    private String expressYn;      // 막차 여부
    private String currentStation; // 현재역
    private String endStation;     // 종착역

    // ------------------- [새로 추가된 부분] -------------------
    private String lineNum;        // 소속된 호선
    // -------------------------------------------------------

    @Lob
    private String stationsJson;   // 열차가 지나갈 역 목록 (JSON)

    private LocalDateTime updateTime; // 마지막 갱신 시간
}
