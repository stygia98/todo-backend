#!/bin/bash
# TodoList Backend - 최초 설치 스크립트
# 아래 파일들을 WinSCP로 전부 업로드한 뒤, EC2에서 이 스크립트를 1회만 실행한다.
#   /home/ec2-user/todo-backend.jar          (빌드된 jar, 파일명 변경 필요)
#   /home/ec2-user/deploy/ (이 폴더 전체)
#   /etc/todolist/todolist.env               (실제 값이 채워진 환경변수 파일)
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_PATH="/home/ec2-user/todo-backend.jar"
ENV_PATH="/etc/todolist/todolist.env"
UPLOAD_DIR="/home/ec2-user/uploads"

echo "=== TodoList Backend Installation ==="
echo ""

echo "[1/6] 필수 파일 확인"
if [ ! -f "$ENV_PATH" ]; then
    echo "  오류: $ENV_PATH 가 없습니다. WinSCP로 todolist.env를 먼저 업로드하세요."
    exit 1
fi
echo "  OK: $ENV_PATH 존재 확인"

if [ ! -f "$JAR_PATH" ]; then
    echo "  오류: $JAR_PATH 가 없습니다. WinSCP로 jar 파일을 todo-backend.jar 이름으로 업로드하세요."
    exit 1
fi
echo "  OK: $JAR_PATH 존재 확인"

echo "[2/6] 로컬 업로드 디렉토리 생성"
# LocalStorageService는 app.storage.type=s3 여도 항상 빈으로 등록된다(과거 LOCAL 레코드 조회용).
# base-dir 이 없으면 빈 생성 자체가 실패하므로 미리 만들어 둔다.
mkdir -p "$UPLOAD_DIR"
echo "  OK: $UPLOAD_DIR"

echo "[3/6] /etc/todolist/todolist.env 권한 정리 (root 전용 600)"
sudo chown root:root "$ENV_PATH"
sudo chmod 600 "$ENV_PATH"

echo "[4/6] systemd 유닛 / journald 설정 배치"
sudo cp "$SCRIPT_DIR/todolist.service" /etc/systemd/system/todolist.service
sudo mkdir -p /etc/systemd/journald.conf.d
sudo cp "$SCRIPT_DIR/todolist.conf" /etc/systemd/journald.conf.d/todolist.conf
sudo systemctl restart systemd-journald

echo "[5/6] 서비스 등록 및 시작"
sudo systemctl daemon-reload
sudo systemctl enable --now todolist

echo "[6/6] 상태 확인"
sleep 3
sudo systemctl status todolist --no-pager || true

echo ""
echo "설치 완료. 로그 확인: sudo journalctl -u todolist -f"
echo "정상 기동 시 로그에서 다음 두 줄을 확인한다."
echo "  - HikariPool-1 - Start completed. (RDS 연결 성공)"
echo "  - Tomcat started on port 8080 (http)"
