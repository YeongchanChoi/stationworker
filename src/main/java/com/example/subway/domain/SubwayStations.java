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
    private String stationcode;

    private String stationname;
    private String stationeng;
    private String linenum;
    private String outercode;
}
