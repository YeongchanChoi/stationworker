package com.example.subway.service;

import com.example.subway.domain.TrainInfo;
import com.example.subway.domain.User;
import com.example.subway.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class NotificationService {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String ALERT_FILE = "alert.json"; // 알림 기록 파일

    public NotificationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 막차 열차에 대해, 해당 열차의 stationsJson(경로)에서
     * 현재역부터 사용자의 근무역까지 남은 정거장이 사용자가 설정한 alertThreshold와 같으면
     * 알림 이벤트를 alert.json 파일에 기록합니다.
     */
    public void checkAndSendAlerts(TrainInfo train) {
        // 막차 여부 확인: "1"이면 막차임
        if (!"1".equals(train.getLstcarAt())) {
            return;
        }

        List<String> route = parseRoute(train.getStationsJson());
        if (route == null || route.isEmpty()) {
            return;
        }

        int currIndex = route.indexOf(train.getCurrentStation());
        if (currIndex == -1) {
            return;
        }

        List<User> users = userRepository.findAll();
        for (User user : users) {
            if (user.getWorkStation() == null) continue;

            int stationIndex = route.indexOf(user.getWorkStation());
            if (stationIndex == -1) continue;

            int remaining = stationIndex - currIndex;
            if (remaining == user.getAlertThreshold()) {
                System.out.println("실행요");
                // 조건에 맞는 경우 alert.json 파일에 알림 이벤트 기록
                logAlertEvent(user, train, remaining);
            }
        }
    }

    // stationsJson 문자열을 List<String>으로 파싱하는 헬퍼 메서드
    private List<String> parseRoute(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (IOException e) {
            log.error("Failed to parse route JSON: {}", json, e);
            return null;
        }
    }

    // 알림 이벤트를 alert.json 파일에 기록하는 헬퍼 메서드
    private void logAlertEvent(User user, TrainInfo train, int remaining) {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        List<Map<String, Object>> alertList = new ArrayList<>();
        File file = new File(ALERT_FILE);

        // 기존 alert.json 파일이 존재하면 기존 기록을 불러옴
        if (file.exists()) {
            try {
                alertList = mapper.readValue(file, new TypeReference<List<Map<String, Object>>>() {});
            } catch (IOException e) {
                log.error("alert.json 파일 읽기 실패: {}", e.getMessage(), e);
            }
        }

        // 새로운 알림 기록 생성
        Map<String, Object> record = new HashMap<>();
        record.put("timestamp", LocalDateTime.now().toString());
        record.put("username", user.getUsername());
        record.put("trainNo", train.getTrainNo());
        record.put("workStation", user.getWorkStation());
        record.put("remainingStops", remaining);
        record.put("message", "Alert: Last train " + train.getTrainNo() +
                " will arrive at your station '" + user.getWorkStation() + "' in " + remaining + " stops.");

        alertList.add(record);

        // alert.json 파일에 기록 저장
        try {
            mapper.writeValue(file, alertList);
            log.info("alert.json 파일에 알림 기록 완료: {}", record);
        } catch (IOException e) {
            log.error("alert.json 파일 쓰기 실패: {}", e.getMessage(), e);
        }
    }
}
