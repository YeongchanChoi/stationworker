# subway_data.py

import json
import pymysql

# 1) line 값 rename을 위한 매핑 딕셔너리
line_rename_map = {
    "인천1호선": "인천선",
    "경의중앙선": "경의선",
    "김포골드라인": "김포도시철도",
    "용인에버라인": "용인경전철",
    "GTXA": "GTX-A"
}

# 2) MySQL 연결 정보 (실제 사용 환경에 맞게 수정)
DB_HOST = 'localhost'
DB_USER = 'root'
DB_PASSWORD = '1234'
DB_NAME = 'test'

def main():
    # 3) data.json 파일 로드
    with open('data.json', 'r', encoding='utf-8') as f:
        data = json.load(f)

    # 4) MySQL 커넥션 및 커서 오픈
    connection = pymysql.connect(
        host=DB_HOST,
        user=DB_USER,
        password=DB_PASSWORD,
        db=DB_NAME,
        charset='utf8mb4'
    )
    cursor = connection.cursor()

    # 혹시 테이블이 아직 없다면 생성 (이미 생성되어 있다면 생략 가능)
    create_table_sql = """
    CREATE TABLE IF NOT EXISTS subway_data (
        id INT AUTO_INCREMENT PRIMARY KEY,
        linenum VARCHAR(50),
        front_stationanme VARCHAR(100),
        front_outercode VARCHAR(50),
        back_stationanme VARCHAR(100),
        back_outercode VARCHAR(50)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
    """
    cursor.execute(create_table_sql)

    # 5) data["DATA"] 순회
    for line_info in data["DATA"]:
        # "line" 필드를 rename_map에 따라 치환
        original_line = line_info["line"]
        linenum = line_rename_map.get(original_line, original_line)  
        # 해당 라인이 매핑 딕셔너리에 없으면 원본 그대로 사용

        # node 배열 순회
        for node_item in line_info["node"]:
            stations = node_item["station"]
            if len(stations) != 2:
                # 만약 station 정보가 2개가 아닌 경우 처리(로그 남기거나 continue 등)
                continue

            # 앞역(첫 번째 station)
            front_station = stations[0]
            front_stationanme = front_station["name"]
            front_outercode   = front_station["fr_code"]

            # 뒷역(두 번째 station)
            back_station = stations[1]
            back_stationanme = back_station["name"]
            back_outercode   = back_station["fr_code"]

            # 6) DB 저장 SQL (공용)
            insert_sql = """
            INSERT INTO subway_data 
                (linenum, front_stationanme, front_outercode, back_stationanme, back_outercode)
            VALUES 
                (%s, %s, %s, %s, %s)
            """

            # 7) 양방향으로 INSERT
            # (1) 앞역 -> 뒷역
            cursor.execute(insert_sql, (
                linenum,
                front_stationanme,
                front_outercode,
                back_stationanme,
                back_outercode
            ))

            # (2) 뒷역 -> 앞역
            cursor.execute(insert_sql, (
                linenum,
                back_stationanme,
                back_outercode,
                front_stationanme,
                front_outercode
            ))

    # 8) 커밋 후 종료
    connection.commit()
    cursor.close()
    connection.close()

if __name__ == "__main__":
    main()
