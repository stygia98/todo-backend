#!/bin/bash
# TodoList Backend - 재배포 스크립트
# 사용법: WinSCP로 새 jar를 /home/ec2-user/todo-backend.jar 로 덮어쓴 뒤 이 스크립트를 실행한다.
set -e

JAR_PATH="/home/ec2-user/todo-backend.jar"

echo "=== TodoList Redeploy ==="

echo "[1/3] jar 파일 확인"
if [ ! -f "$JAR_PATH" ]; then
    echo "  오류: $JAR_PATH 가 없습니다. WinSCP로 새 jar를 먼저 업로드하세요."
    exit 1
fi
echo "  OK: $JAR_PATH ($(date -r "$JAR_PATH" '+%Y-%m-%d %H:%M:%S') 수정됨)"

echo "[2/3] 서비스 재시작"
sudo systemctl restart todolist

echo "[3/3] 상태 확인"
sleep 3
sudo systemctl status todolist --no-pager || true

echo ""
echo "재배포 완료. 실시간 로그 확인: sudo journalctl -u todolist -f"
