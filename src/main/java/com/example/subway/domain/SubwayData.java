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
    private Long id;

    private String linenum;

    private String frontStationname;
    private String frontOutercode;

    private String backStationname;
    private String backOutercode;

}
