#!/usr/bin/env bash
# ==============================================================================
# IRO Studio 3.0 — Tự động triển khai lên máy chủ Linux / HomeLab / VPS
# ==============================================================================
set -e

GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${CYAN}======================================================${NC}"
echo -e "${GREEN}   IRO Studio 3.0 — Trình triển khai máy chủ tự động  ${NC}"
echo -e "${CYAN}======================================================${NC}"

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

# 1. Phát hiện IP mạng nội bộ / Internet
SERVER_IP=$(hostname -I 2>/dev/null | awk '{print $1}' || echo "127.0.0.1")

# 2. Kiểm tra nếu máy có Docker & Docker Compose (Khuyên dùng nhất)
if command -v docker &> /dev/null; then
    echo -e "${GREEN}[✔] Phát hiện Docker trên máy chủ.${NC}"
    
    PORT=${PORT:-8080}
    echo -e "${CYAN}[i] Đang khởi động container IRO Studio tại cổng: ${PORT}${NC}"

    if docker compose version &> /dev/null; then
        PORT=$PORT docker compose up -d --build
    elif command -v docker-compose &> /dev/null; then
        PORT=$PORT docker-compose up -d --build
    else
        echo -e "${YELLOW}[!] Build và chạy bằng Docker trực tiếp...${NC}"
        docker build -t iro-studio:3.0 .
        docker stop iro-studio-web 2>/dev/null || true
        docker rm iro-studio-web 2>/dev/null || true
        docker run -d -p "${PORT}:80" --name iro-studio-web --restart unless-stopped iro-studio:3.0
    fi

    echo -e "\n${GREEN}======================================================${NC}"
    echo -e "${GREEN}  Triển khai thành công với Docker!${NC}"
    echo -e "  Truy cập cục bộ:  ${CYAN}http://localhost:${PORT}${NC}"
    echo -e "  Truy cập mạng LAN: ${CYAN}http://${SERVER_IP}:${PORT}${NC}"
    echo -e "${GREEN}======================================================${NC}\n"
    exit 0
fi

# 3. Nếu không có Docker, kiểm tra Node.js
if command -v node &> /dev/null; then
    echo -e "${GREEN}[✔] Phát hiện Node.js ($(node -v)).${NC}"
    
    # Kiểm tra PM2
    if command -v pm2 &> /dev/null; then
        echo -e "${CYAN}[i] Khởi chạy dịch vụ ngầm với PM2...${NC}"
        pm2 delete iro-studio 2>/dev/null || true
        PORT=${PORT:-4173} pm2 start server.mjs --name "iro-studio"
        pm2 save
        echo -e "\n${GREEN}======================================================${NC}"
        echo -e "${GREEN}  Triển khai thành công với PM2!${NC}"
        echo -e "  Truy cập: ${CYAN}http://${SERVER_IP}:${PORT:-4173}${NC}"
        echo -e "${GREEN}======================================================${NC}\n"
        exit 0
    else
        PORT=${PORT:-4173}
        echo -e "${YELLOW}[i] Chạy trực tiếp qua Node.js (cổng ${PORT})...${NC}"
        echo -e "  Truy cập: ${CYAN}http://${SERVER_IP}:${PORT}${NC}"
        PORT=$PORT node server.mjs
        exit 0
    fi
fi

# 4. Nếu không có cả Docker lẫn Node.js
echo -e "${RED}[✘] Không tìm thấy Docker hoặc Node.js trên máy chủ.${NC}"
echo -e "${YELLOW}Vui lòng cài đặt một trong hai môi trường:${NC}"
echo -e "  1. Docker (khuyên dùng): ${CYAN}curl -fsSL https://get.docker.com | sh${NC}"
echo -e "  2. Hoặc Node.js:         ${CYAN}sudo apt update && sudo apt install -y nodejs${NC}"
echo -e "  3. Hoặc copy toàn bộ thư mục này vào thư mục web của Nginx / Apache / cPanel."
exit 1
