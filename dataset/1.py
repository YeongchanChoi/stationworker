# subway_stations.py

import pandas as pd
import mysql.connector
import chardet

# CSV 파일 경로
csv_file_path = 'a.csv'

# MySQL 연결 정보
db_config = {
    'host': 'localhost',        # MySQL 호스트
    'user': 'root',    # MySQL 사용자 이름
    'password': '1234',# MySQL 비밀번호
    'database': 'test' # MySQL 데이터베이스 이름
}

# MySQL 테이블 생성 쿼리
create_table_query = """
CREATE TABLE IF NOT EXISTS subway_stations (
    stationcode VARCHAR(10) NOT NULL,
    stationname VARCHAR(100),
    stationeng VARCHAR(100),
    linenum VARCHAR(20),
    outercode VARCHAR(10),
    PRIMARY KEY (stationcode)
);
"""

# CSV 데이터를 MySQL에 저장
def load_csv_to_mysql(csv_path, db_config):
    # MySQL 연결
    conn = mysql.connector.connect(**db_config)
    cursor = conn.cursor()

    # 테이블 생성
    cursor.execute(create_table_query)

    # 파일의 인코딩 감지
    with open(csv_path, 'rb') as file:
        raw_data = file.read()
        result = chardet.detect(raw_data)
        detected_encoding = result['encoding']

    # 감지된 인코딩으로 CSV 읽기
    data = pd.read_csv(csv_path, encoding=detected_encoding)

    # 호선명이 '0'으로 시작하면 제거
    data['호선'] = data['호선'].str.lstrip('0')

    # 데이터 삽입 쿼리
    insert_query = """
    INSERT INTO subway_stations (stationcode, stationname, stationeng, linenum, outercode)
    VALUES (%s, %s, %s, %s, %s)
    ON DUPLICATE KEY UPDATE
        stationname=VALUES(stationname),
        stationeng=VALUES(stationeng),
        linenum=VALUES(linenum),
        outercode=VALUES(outercode);
    """

    # 데이터 삽입
    for _, row in data.iterrows():
        cursor.execute(insert_query, (
            row['전철역코드'],
            row['전철역명'],
            row['전철명명(영문)'],
            row['호선'],
            row['외부코드']
        ))

    # 커밋 및 연결 종료
    conn.commit()
    cursor.close()
    conn.close()

# 함수 호출
load_csv_to_mysql(csv_file_path, db_config)
