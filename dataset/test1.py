# train_info.py

import requests
import xml.etree.ElementTree as ET
import pymysql
from collections import deque

# -----------------------------
# DB 접속 정보
# -----------------------------
DB_HOST = 'localhost'
DB_USER = 'root'
DB_PASSWORD = '1234'
DB_NAME = 'test'

# -----------------------------
# BFS로 단일 경로 찾기 (과정 출력 추가)
# -----------------------------
def bfs_path(graph, start, end):
    """
    그래프(인접 리스트)에서 start~end 사이의 '단일 경로'를 찾아 리스트로 반환.
    찾지 못하면 빈 리스트([]).
    BFS 과정을 출력하여 어디까지 탐색되었는지 확인할 수 있음.
    """
    if start not in graph or end not in graph:
        print(f"[BFS] 시작역 '{start}' 또는 종착역 '{end}'이 그래프에 존재하지 않습니다.")
        return []

    visited = set()
    parent = {}
    queue = deque()

    visited.add(start)
    parent[start] = None
    queue.append(start)

    print(f"[BFS] 시작: {start}")
    step = 1

    while queue:
        current = queue.popleft()
        print(f"[BFS] Step {step}: 현재 노드 '{current}'을(를) 방문함.")
        step += 1

        if current == end:
            print("[BFS] 종착역을 발견하여 탐색을 종료합니다.")
            break

        for neighbor in graph[current]:
            if neighbor not in visited:
                visited.add(neighbor)
                parent[neighbor] = current
                queue.append(neighbor)
                print(f"        -> '{neighbor}'을(를) 큐에 추가함.")

    # end까지 미도달 → 경로 없음
    if end not in parent:
        print("[BFS] 경로를 찾지 못했습니다.")
        print(f"[BFS] 탐색된 노드: {visited}")
        return []

    # parent를 역추적 (end → start 역순)
    path = []
    node = end
    while node is not None:
        path.append(node)
        node = parent[node]

    # 뒤집어서 start→end 순서
    path.reverse()
    return path

def main():
    # -----------------------------
    # 1) DB 연결 및 그래프 구성
    # -----------------------------
    connection = pymysql.connect(
        host=DB_HOST,
        user=DB_USER,
        password=DB_PASSWORD,
        db=DB_NAME,
        charset='utf8mb4',
        cursorclass=pymysql.cursors.DictCursor  # Use DictCursor for easier access
    )
    cursor = connection.cursor()

    # subway_data 테이블에서 "8호선"만 추출
    select_sql = """
        SELECT front_stationanme, back_stationanme
        FROM subway_data
        WHERE linenum = %s
    """
    cursor.execute(select_sql, ("1호선",))
    rows = cursor.fetchall()


    # 인접 리스트 구성
    graph = {}

    def add_edge(a, b):
        if a not in graph:
            graph[a] = []
        if b not in graph[a]:
            graph[a].append(b)

    for row in rows:
        front_station = row['front_stationanme']
        back_station = row['back_stationanme']
        add_edge(front_station, back_station)
        # 지하철은 양방향으로 가정; 역방향 간선 추가
        add_edge(back_station, front_station)

    # -----------------------------
    # 2) 서울 열린데이터광장 실시간 API 호출 (하나의 열차만)
    # -----------------------------
    # API 호출 범위를 0부터 10으로 설정하여 최대 10개의 열차 조회 (원래는 0부터 0)
    api_url = "http://swopenAPI.seoul.go.kr/api/subway/755455636167793236306850475a51/xml/realtimePosition/0/10/1호선"
    response = requests.get(api_url)
    if response.status_code != 200:
        print(f"[ERROR] API 호출 실패 (status: {response.status_code})")
        return

    # XML 파싱
    root = ET.fromstring(response.text)

    # 결과 코드 체크
    result_code_node = root.find("./RESULT/CODE")
    if result_code_node is not None and result_code_node.text != 'INFO-000':
        result_msg_node = root.find("./RESULT/MESSAGE")
        print(f"[ERROR] API 응답 에러: {result_code_node.text}, {result_msg_node.text}")
        return

    # 열차 정보를 담고 있는 노드(row) 가져오기
    rows_xml = root.findall(".//row")
    if not rows_xml:
        print("[INFO] 8호선 열차 정보를 찾지 못했습니다.")
        return

    # -----------------------------
    # 3) 하나의 열차 선택 및 DB 삽입
    # -----------------------------
    target_row = rows_xml[0]  # 첫 번째 열차 정보만 사용

    # 열차 정보 추출
    train_no = target_row.findtext("trainNo")

    if not train_no:
        print("[ERROR] 열차번호(trainNo) 정보가 없습니다.")
        return

    print("[열차 정보]")
    print(f" - trainNo  : {train_no}")
    print()

    # -----------------------------
    # 4) DB에 열차번호 삽입
    # -----------------------------
    # 먼저, train_info 테이블이 존재하지 않으면 생성
    create_table_sql = """
        CREATE TABLE IF NOT EXISTS train_info (
            id INT AUTO_INCREMENT PRIMARY KEY,
            train_no VARCHAR(10) NOT NULL,
            timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
    """
    cursor.execute(create_table_sql)

    # 열차번호 삽입
    insert_sql = """
        INSERT INTO train_info (train_no)
        VALUES (%s)
    """
    cursor.execute(insert_sql, (train_no,))
    connection.commit()
    print(f"[DB] trainNo={train_no}이(가) train_info 테이블에 삽입되었습니다.")

    # -----------------------------
    # 5) BFS로 경로 찾기 (현재역 -> 종착역)
    # -----------------------------
    # 현재역과 종착역을 가져옵니다.
    statn_nm   = target_row.findtext("statnNm")     # 현재 역
    statn_tnm  = target_row.findtext("statnTnm")    # 종착역 이름

    if not statn_nm or not statn_tnm:
        print("[ERROR] 현재역 또는 종착역 정보가 없습니다.")
        return

    print(f"[현재역]: {statn_nm}, [종착역]: {statn_tnm}")
    print("[BFS 경로 탐색 시작]")
    path = bfs_path(graph, statn_nm, statn_tnm)

    if not path:
        print(f"[BFS 경로] {statn_nm}에서 {statn_tnm}까지 경로를 찾을 수 없습니다.")
    else:
        print(f"[BFS 경로] {statn_nm} → {statn_tnm}")
        for i, station in enumerate(path, start=1):
            print(f"{i}. {station}")

    # DB 연결 종료
    cursor.close()
    connection.close()

if __name__ == "__main__":
    main()
