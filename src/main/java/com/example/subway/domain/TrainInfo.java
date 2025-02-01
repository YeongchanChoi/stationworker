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

    // API에서 받아오는 필드들
    private String subwayId;     // 지하철호선ID (예: 1001, 1063 등)
    private String statnId;      // 지하철역ID (현재역 ID)
    private String trainNo;      // 열차번호
    private String lastRecptnDt; // 최종수신날짜
    private String recptnDt;     // 최종수신시간
    private String upDown;       // 상하행 정보 (0: 상행/내선, 1: 하행/외선)
    private String trainSttus;   // 열차상태구분 (0:진입, 1:도착, 2:출발, 3:전역출발)
    private String directAt;     // 급행여부 (1:급행, 0:아님, 7:특급)
    private String lstcarAt;     // 막차여부 (1:막차, 0:아님)

    // API에서 받아온 현재역, 종착역명에서 소괄호 제거 후 저장
    private String currentStation; // 현재역 (statnNm, 소괄호 제거)
    private String statnTid;       // 종착지하철역ID
    private String endStation;     // 종착역 (statnTnm, 소괄호 제거)

    // 추가 정보: DB에서 사용하는 노선명 (예: "1호선", "경의선" 등)
    private String lineNum;

    @Lob
    private String stationsJson;   // 열차가 지나갈 역 목록 (JSON)

    private LocalDateTime updateTime; // 마지막 갱신 시간
}
