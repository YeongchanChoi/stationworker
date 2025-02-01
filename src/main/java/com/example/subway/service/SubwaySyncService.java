package com.example.subway.service;

import com.example.subway.domain.TrainInfo;
import com.example.subway.repository.TrainInfoRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class SubwaySyncService {

    private final TrainInfoRepository trainInfoRepository;
    private final SubwayGraphService subwayGraphService;

    // 최초 API 키
    private String apiKey = "755455636167793236306850475a51";
    // API URL 템플릿
    private static final String SUBWAY_API_URL
            = "http://swopenAPI.seoul.go.kr/api/subway/%s/xml/realtimePosition/0/100/%s";
    // 로그 기록 파일명
    private static final String LOG_FILE = "log.json";

    public SubwaySyncService(TrainInfoRepository trainInfoRepository,
                             SubwayGraphService subwayGraphService) {
        this.trainInfoRepository = trainInfoRepository;
        this.subwayGraphService  = subwayGraphService;
    }

    /**
     * 지정된 주기로 각 호선의 열차정보를 동기화합니다.
     */
    @Scheduled(fixedDelayString = "${myapp.schedule.subway-refresh}")
    public void syncTrains() {
        Map<String, String> linesToSync = new LinkedHashMap<>();

        for (int line = 1; line <= 9; line++) {
            String lineKey = line + "호선";
            linesToSync.put(lineKey, lineKey);
        }
        linesToSync.put("경강선", "경강선");
        linesToSync.put("경의중앙선", "경의선");  // API는 "경의중앙선", DB에는 "경의선"으로 저장
        linesToSync.put("경춘선", "경춘선");
        linesToSync.put("공항철도", "공항철도");
        linesToSync.put("서해선", "서해선");
        linesToSync.put("수인분당선", "수인분당선");
        linesToSync.put("신분당선", "신분당선");
        linesToSync.put("신림선", "신림선");
        linesToSync.put("우이신설선", "우이신설선");

        for (Map.Entry<String, String> entry : linesToSync.entrySet()) {
            String apiLineName = entry.getKey();
            String dbLineName  = entry.getValue();
            try {
                String lineNameEncoded = URLEncoder.encode(apiLineName, "UTF-8");
                syncLine(lineNameEncoded, dbLineName);
            } catch (Exception e) {
                log.error("Failed to sync line {}: {}", apiLineName, e.getMessage(), e);
            }
        }
    }

    /**
     * 특정 호선(lineNameEncoded, lineNum)에 대한 열차정보 동기화 및 DB 정리 로직
     */
    private void syncLine(String lineNameEncoded, String lineNum) throws Exception {
        // 인스턴스 변수 apiKey 사용
        String apiUrl = String.format(SUBWAY_API_URL, apiKey, lineNameEncoded);
        log.info("[SYNC] {}: API 호출 URL = {}", lineNum, apiUrl);

        URL url = new URL(apiUrl);
        URLConnection connection = url.openConnection();
        String contentType = connection.getContentType();
        InputStream is = connection.getInputStream();

        // JSON 형식 응답 처리
        if (contentType != null && contentType.contains("application/json")) {
            String jsonResponse = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> responseMap = mapper.readValue(jsonResponse, new TypeReference<Map<String, Object>>() {});
            if ("ERROR-337".equals(responseMap.get("code"))) {
                // 현재 사용중인 API 키에 따라 순차적으로 교체
                if (this.apiKey.equals("755455636167793236306850475a51")) {
                    this.apiKey = "51434941476779323635637044786e";
                } else if (this.apiKey.equals("51434941476779323635637044786e")) {
                    this.apiKey = "4f6b52794467793236325572567469";
                }
                log.warn("[ERROR] {}: {} (API 키를 새 값으로 교체함)", lineNum, jsonResponse);
                return;
            }
            log.info("[INFO] {}: JSON 응답 수신: {}", lineNum, jsonResponse);
            return;
        } else {
            // XML 형식 응답 처리
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(is);
            doc.getDocumentElement().normalize();

            // <code> 태그를 통해 오류 코드 확인
            NodeList codeNodes = doc.getElementsByTagName("code");
            if (codeNodes != null && codeNodes.getLength() > 0) {
                String errorCode = codeNodes.item(0).getTextContent();
                if ("ERROR-337".equals(errorCode)) {
                    if (this.apiKey.equals("755455636167793236306850475a51")) {
                        this.apiKey = "51434941476779323635637044786e";
                    } else if (this.apiKey.equals("51434941476779323635637044786e")) {
                        this.apiKey = "4f6b52794467793236325572567469";
                    }
                    log.warn("[ERROR] {}: 응답 오류 코드 {} 발견 (API 키를 새 값으로 교체함)", lineNum, errorCode);
                    return;
                }
            }

            // 정상적인 XML 응답인 경우, 기존 로직대로 열차 정보를 처리합니다.
            NodeList rowList = doc.getElementsByTagName("row");
            if (rowList.getLength() == 0) {
                log.info("[INFO] {}: 열차 정보가 없습니다.", lineNum);
                return;
            }

            // 일반 노선용 그래프만 생성 (급행 여부와 무관하게 동일한 경로 탐색)
            Map<String, List<String>> graph = subwayGraphService.buildGraph(lineNum, false);

            // 이번 동기화에서 발견된 trainNo 추적
            Set<String> fetchedTrainNos = new HashSet<>();

            for (int i = 0; i < rowList.getLength(); i++) {
                Node row = rowList.item(i);
                if (row.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element element = (Element) row;

                // API의 각 필드 값 추출
                String subwayId      = getTagValue(element, "subwayId");
                String statnId       = getTagValue(element, "statnId");
                String trainNo       = getTagValue(element, "trainNo");
                String lastRecptnDt  = getTagValue(element, "lastRecptnDt");
                String recptnDt      = getTagValue(element, "recptnDt");
                String upDown        = getTagValue(element, "updnLine");
                String statnTid      = getTagValue(element, "statnTid");
                String rawCurrStatn  = getTagValue(element, "statnNm");
                String rawEndStatn   = getTagValue(element, "statnTnm");
                String trainSttus    = getTagValue(element, "trainSttus");
                String directAt      = getTagValue(element, "directAt");
                String lstcarAt      = getTagValue(element, "lstcarAt");

                // 현재역 및 종착역의 소괄호와 그 안의 내용 제거
                String currStatn = removeParentheses(rawCurrStatn);
                String endStatn  = removeParentheses(rawEndStatn);

                if (trainNo == null || currStatn == null || endStatn == null) {
                    log.warn("Skipping row due to null values: trainNo={}, currStatn={}, endStatn={}",
                            trainNo, currStatn, endStatn);
                    continue;
                }

                fetchedTrainNos.add(trainNo);

                Optional<TrainInfo> optTrain = trainInfoRepository.findByTrainNo(trainNo);
                TrainInfo train;
                if (optTrain.isEmpty()) {
                    // --- [새 열차 추가] ---
                    train = new TrainInfo();
                    train.setSubwayId(subwayId);
                    train.setStatnId(statnId);
                    train.setTrainNo(trainNo);
                    train.setLastRecptnDt(lastRecptnDt);
                    train.setRecptnDt(recptnDt);
                    train.setUpDown(upDown);
                    train.setTrainSttus(trainSttus);
                    train.setDirectAt(directAt);
                    train.setLstcarAt(lstcarAt);
                    train.setCurrentStation(currStatn);
                    train.setStatnTid(statnTid);
                    train.setEndStation(endStatn);
                    train.setLineNum(lineNum);

                    // BFS를 통해 현재역 ~ 종착역 경로 계산 (항상 일반 열차용 graph 사용)
                    List<String> path = subwayGraphService.bfsPath(graph, currStatn, endStatn);
                    log.debug("[NEW TRAIN] BFS path: {}", path);

                    train.setStationsJson(toJsonString(path));
                    train.setUpdateTime(LocalDateTime.now());
                    trainInfoRepository.save(train);

                    log.info("[NEW TRAIN] {} - {} ( {} -> {} )", trainNo, lineNum, currStatn, endStatn);
                    // log.json에 신규 열차 추가 이벤트 기록
                    logTrainEvent("NEW_TRAIN", train);

                } else {
                    // --- [기존 열차 업데이트] ---
                    train = optTrain.get();
                    train.setSubwayId(subwayId);
                    train.setStatnId(statnId);
                    train.setTrainNo(trainNo);
                    train.setLastRecptnDt(lastRecptnDt);
                    train.setRecptnDt(recptnDt);
                    train.setUpDown(upDown);
                    train.setTrainSttus(trainSttus);
                    train.setDirectAt(directAt);
                    train.setLstcarAt(lstcarAt);
                    train.setCurrentStation(currStatn);
                    train.setStatnTid(statnTid);
                    train.setEndStation(endStatn);
                    train.setLineNum(lineNum);

                    List<String> path = fromJsonString(train.getStationsJson());
                    if (path != null && !path.isEmpty()) {
                        int currIdx = path.indexOf(currStatn);
                        if (currIdx > 0) {
                            for (int idx = 0; idx < currIdx; idx++) {
                                path.set(idx, null);
                            }
                            path.removeIf(Objects::isNull);
                        }
                        train.setStationsJson(toJsonString(path));
                    }
                    train.setUpdateTime(LocalDateTime.now());
                    trainInfoRepository.save(train);

                    log.info("[UPDATE TRAIN] {} - {} (현재역: {})", trainNo, lineNum, currStatn);
                    // log.json에 업데이트 이벤트 기록
                    logTrainEvent("UPDATE_TRAIN", train);
                }
            }


// 삭제 이벤트 처리 (삭제되는 열차에 대해 log.json에도 기록)
            List<TrainInfo> existingTrains = trainInfoRepository.findByLineNum(lineNum);
            for (TrainInfo t : existingTrains) {
                if (!fetchedTrainNos.contains(t.getTrainNo())) {
                    trainInfoRepository.delete(t);
                    log.info("[DELETE TRAIN] {} - {} (운행 종료)", t.getTrainNo(), lineNum);
                    // 삭제 이벤트도 log.json에 기록
                    logTrainEvent("DELETE_TRAIN", t);
                }
            }

        }
    }

    private String getTagValue(Element element, String tag) {
        NodeList list = element.getElementsByTagName(tag);
        if (list.getLength() == 0) {
            return null;
        }
        return list.item(0).getTextContent();
    }

    private String toJsonString(List<String> list) {
        if (list == null) return "[]";
        return "[" + String.join(",", list.stream().map(s -> "\"" + s + "\"").toList()) + "]";
    }

    private List<String> fromJsonString(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            if (trimmed.isEmpty()) {
                return new ArrayList<>();
            }
            List<String> result = new ArrayList<>();
            String[] tokens = trimmed.split(",");
            for (String t : tokens) {
                String val = t.trim().replaceAll("\"", "");
                result.add(val);
            }
            return result;
        }
        return new ArrayList<>();
    }

    private String removeParentheses(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("\\(.*?\\)", "").trim();
    }

    /**
     * 새로운 열차 추가 혹은 기존 열차 업데이트 이벤트를 log.json 파일에 기록합니다.
     * 각 기록에는 timestamp, action, trainNo, lineNum, currentStation, endStation 등의 정보가 포함됩니다.
     */
    private void logTrainEvent(String action, TrainInfo train) {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        List<Map<String, Object>> logList = new ArrayList<>();
        File file = new File(LOG_FILE);

        // 기존 log.json 파일이 존재하면 내용을 불러옵니다.
        if (file.exists()) {
            try {
                logList = mapper.readValue(file, new TypeReference<List<Map<String, Object>>>() {});
            } catch (IOException e) {
                log.error("log.json 파일 읽기 실패: {}", e.getMessage(), e);
            }
        }

        // 새 로그 기록 생성
        Map<String, Object> record = new HashMap<>();
        record.put("timestamp", LocalDateTime.now().toString());
        record.put("action", action);
        record.put("trainNo", train.getTrainNo());
        record.put("lineNum", train.getLineNum());
        record.put("currentStation", train.getCurrentStation());
        record.put("endStation", train.getEndStation());
        record.put("upDown", train.getUpDown());
        record.put("express", train.getDirectAt());
        logList.add(record);

        // log.json 파일에 기록 저장
        try {
            mapper.writeValue(file, logList);
            log.info("log.json 파일에 {} 이벤트 기록 완료: {}", action, record);
        } catch (IOException e) {
            log.error("log.json 파일 쓰기 실패: {}", e.getMessage(), e);
        }
    }
}
