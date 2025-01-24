package com.example.subway.service;

import com.example.subway.domain.SubwayData;
import com.example.subway.repository.SubwayDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class SubwayGraphService {

    private final SubwayDataRepository subwayDataRepository;

    public SubwayGraphService(SubwayDataRepository subwayDataRepository) {
        this.subwayDataRepository = subwayDataRepository;
    }

    // 특정 호선에 대해 인접 리스트 형태의 그래프를 구성
    public Map<String, List<String>> buildGraph(String lineNum) {
        // 1) DB에서 전체 SubwayData 로딩
        List<SubwayData> dataList = subwayDataRepository.findAll();

        // 2) 로드된 모든 레코드 로그 출력 (디버그용)
        log.info("===== [SubwayData 테이블 전체 조회 결과] =====");
        for (SubwayData data : dataList) {
            log.info("Row => id={}, linenum={}, frontStationname={}, frontOutercode={}, backStationname={}, backOutercode={}",
                    data.getId(),
                    data.getLinenum(),
                    data.getFrontStationname(),
                    data.getFrontOutercode(),
                    data.getBackStationname(),
                    data.getBackOutercode()
            );
        }
        log.info("===== [총 {}개 레코드 조회 완료] =====", dataList.size());

        // 3) lineNum에 해당하는 레코드만 골라서 그래프 구성
        Map<String, List<String>> graph = new HashMap<>();
        for (SubwayData data : dataList) {
            // lineNum 일치하는 것만 처리
            if (!data.getLinenum().equals(lineNum)) {
                continue;
            }

            String front = data.getFrontStationname();
            String back  = data.getBackStationname();

            // 인접 리스트에 추가
            graph.computeIfAbsent(front, k -> new ArrayList<>()).add(back);
            graph.computeIfAbsent(back, k -> new ArrayList<>()).add(front);
        }

        return graph;
    }

    // BFS로 start~end 경로 찾기
    public List<String> bfsPath(Map<String, List<String>> graph, String start, String end) {
        log.info("----- BFS START: from '{}' to '{}' -----", start, end);

        // 그래프에 start나 end가 없으면 빈 리스트 반환
        if (!graph.containsKey(start) || !graph.containsKey(end)) {
            log.warn("[BFS] start='{}' 혹은 end='{}'가 graph에 존재하지 않습니다. 빈 경로 반환.", start, end);
            return Collections.emptyList();
        }

        Set<String> visited = new HashSet<>();
        Map<String, String> parent = new HashMap<>();
        Queue<String> queue = new LinkedList<>();

        visited.add(start);
        parent.put(start, null);
        queue.offer(start);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            log.debug("[BFS] 현재 방문 노드: {}", current);

            // end 지점에 도달
            if (current.equals(end)) {
                log.debug("[BFS] 목적지 '{}'에 도달했습니다.", end);
                break;
            }

            List<String> neighbors = graph.get(current);
            if (neighbors == null) {
                continue;
            }
            for (String neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parent.put(neighbor, current);
                    queue.offer(neighbor);
                    log.debug("[BFS] 새로운 이웃 '{}' 발견 -> queue에 추가", neighbor);
                }
            }
        }

        // end 노드가 parent에 없으면 경로가 없다는 뜻
        if (!parent.containsKey(end)) {
            log.warn("[BFS] '{}'로 가는 경로를 찾지 못했습니다. 빈 경로 반환.", end);
            return Collections.emptyList();
        }

        // parent 맵을 역추적
        List<String> path = new ArrayList<>();
        String node = end;
        while (node != null) {
            path.add(node);
            node = parent.get(node);
        }
        Collections.reverse(path);

        log.info("[BFS] '{}' -> '{}' 경로: {}", start, end, path);
        return path;
    }
}
