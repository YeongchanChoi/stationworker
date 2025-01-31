package com.example.subway.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class SubwayData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; //고유 id

    private String linenum; //몇호선에 존재하는 관계인지

    /*연결관계는 앞>뒤로 연결됨, 앞 > 뒤의 연결관계 방향에 따라 upDown 상행 하행을 저장*/
    private String frontStationname; //앞 역 이름
    private String frontOutercode; //앞 역 외부코드

    private String backStationname; //뒷 역 이름
    private String backOutercode; //뒷 역 외부코드


    private String upDown; //상행 하행 저장정보. 상행(내선):0 , 하행(외선):1

}
