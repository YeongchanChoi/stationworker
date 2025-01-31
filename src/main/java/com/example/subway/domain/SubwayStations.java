package com.example.subway.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class SubwayStations {

    @Id
    private String stationcode; //역 코드 id

    private String stationname; //역 이름
    private String stationeng; //역 이름 영문
    private String linenum; //역에 지나는 호선
    private String outercode; //역의 외부코드
    /*역에 지나는 호선이 여러개인 경우, 지나는 호선 개수만큼 하나의 역에 여러개의 외부코드가 있음.
     현재 저장된 방식은 하나의 역마다 여러개의 외부코드가 있을 경우 여러 행에 저장됨.
     예시)
     0150	서울역	Seoul Station	1호선	133
     0426	서울역	Seoul Station	4호선	426
     1251	서울역	Seoul Station	경의선	P313
     4201	서울역	Seoul Station	공항철도	A01
     */
}
