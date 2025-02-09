package com.example.subway.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 아이디(유니크)
    @Column(unique = true, nullable = false)
    private String username;

    // 비밀번호 (암호화하여 저장)
    @Column(nullable = false)
    private String password;

    // 사용자의 근무역
    private String workStation;

    // 열차가 도착하기 전에 알림을 받을 정거장 수
    private int alertThreshold;
}
