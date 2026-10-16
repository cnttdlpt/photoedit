# HƯỚNG DẪN TRIỂN KHAI IRO STUDIO 3.0 LÊN MÁY CHỦ (SERVER DEPLOYMENT GUIDE)

Bộ mã nguồn `web/` là phiên bản web độc lập (Standalone Web Studio) được kế thừa và chuyển đổi trọn vẹn từ ứng dụng **IRO Studio 3.0 Android / iOS**, bao gồm:
- **Thư viện 392 công thức màu phim** Fujifilm X Weekly (`recipes.json`) với bộ tìm kiếm và lọc cảm biến tức thì.
- **30 mẫu khung ảnh triển lãm (PhotoFrame)** chuẩn bảo tàng và hoài cổ.
- **LightCraft Suite**: Đường cong Tone Curve tương tác Fritsch–Carlson và Bộ 3 bánh xe màu điện ảnh 3-Way Color Wheels (OkLab / OkLCh).
- **Phòng tối quang học 3.0**: Black Pro-Mist, Halation CineStill 800T, Bleach Bypass, Dehaze, Texture, Swirly Bokeh Helios 44-2, 3D Depth Fog, Light Leak 35mm, Hạt muối bạc Dual-Band và Chuyển động Living Film 24fps.
- **Xuất ảnh đa định dạng**: JPEG, WebP, PNG (nhúng ẩn công thức LSB) và xuất bảng màu 3D LUT `.cube` cho DaVinci Resolve, Premiere, CapCut.
- **Không phụ thuộc database / Zero Backend**: 100% xử lý ảnh thuần Client-side qua Canvas & Web Workers, máy chủ chỉ cần đóng vai trò Web Server tĩnh siêu nhẹ, an toàn tuyệt đối và chịu tải hàng triệu lượt truy cập cùng lúc.

---

## ⚡ CÁCH 0: TRIỂN KHAI NHANH 1 CLICK BẰNG SCRIPT TỰ ĐỘNG
Nếu máy chủ của bạn chạy Ubuntu, Debian, CentOS, Rocky Linux hoặc Alpine:
```bash
cd web
chmod +x deploy.sh
./deploy.sh
```
*Script sẽ tự động kiểm tra Docker / Docker Compose hoặc Node.js / PM2 có sẵn trên máy để chọn phương thức tối ưu nhất và khởi chạy ngay lập tức.*

---

## 🐳 CÁCH 1: CHẠY BẰNG DOCKER & DOCKER COMPOSE (Khuyên dùng cho Homelab / VPS / Cloud)

### 1.1. Dùng Docker Compose (Chuẩn nhất)
Trong thư mục `web/`, chạy:
```bash
docker compose up -d --build
```
- Mặc định container Nginx Alpine siêu nhẹ (~25MB) sẽ khởi chạy tại cổng **8080**.
- Truy cập: `http://ip-may-chu:8080`.

**Đổi cổng tuỳ ý:**
Nếu muốn chạy ở cổng khác (ví dụ cổng 80 chuẩn HTTP):
```bash
PORT=80 docker compose up -d --build
```
Hoặc tạo file `.env` (từ `.env.example`):
```bash
cp .env.example .env
# Chỉnh PORT=80 trong file .env
docker compose up -d
```

### 1.2. Dùng lệnh Docker CLI trực tiếp
```bash
docker build -t iro-studio:3.0 .
docker run -d -p 8080:80 --name iro-studio-web --restart unless-stopped iro-studio:3.0
```

---

## 🌐 CÁCH 2: CHẠY TRÊN MÁY CHỦ VPS LINUX VỚI NGINX

### 1. Sao chép mã nguồn lên thư mục web trên VPS
```bash
sudo mkdir -p /var/www/iro-studio
sudo cp -r web/* /var/www/iro-studio/
sudo chown -R www-data:www-data /var/www/iro-studio
```

### 2. Cấu hình Nginx
Mở file cấu hình Nginx:
```bash
sudo nano /etc/nginx/sites-available/iro-studio
```
Dán cấu hình (đã chuẩn hóa MIME type cho `.cube`, `.mjs`, CSS và Gzip):
```nginx
server {
    listen 80;
    server_name your-domain.com www.your-domain.com; # Thay bằng tên miền hoặc IP của bạn

    root /var/www/iro-studio;
    index index.html;

    gzip on;
    gzip_vary on;
    gzip_proxied any;
    gzip_comp_level 6;
    gzip_types text/plain text/css application/json application/javascript image/svg+xml;

    include /etc/nginx/mime.types;
    default_type application/octet-stream;

    types {
        text/plain cube;
        text/javascript mjs;
    }

    location / {
        try_files $uri $uri/ /index.html;
        add_header Cache-Control "no-cache";
        add_header Access-Control-Allow-Origin "*";
        add_header X-Content-Type-Options "nosniff";
    }

    location ~* \.(?:css|js|mjs|jpg|jpeg|png|webp|svg|cube|woff2)$ {
        expires 30d;
        add_header Cache-Control "public";
    }
}
```

Kích hoạt và nạp lại Nginx:
```bash
sudo ln -s /etc/nginx/sites-available/iro-studio /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

---

## 🔒 CÁCH 3: CHẠY BẰNG CADDY (Tự động cấp phát chứng chỉ HTTPS SSL Let's Encrypt)
Caddyfile đi kèm đã được tối ưu sẵn. Bạn chỉ cần sửa `:80` thành tên miền của bạn (ví dụ: `studio.yourdomain.com`):
```bash
# Cài đặt Caddy trên Ubuntu/Debian
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https
curl -1sLF 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLF 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update && sudo apt install -y caddy

# Chạy với Caddyfile có sẵn
sudo cp web/Caddyfile /etc/caddy/Caddyfile
sudo cp -r web/* /usr/share/caddy/
sudo systemctl restart caddy
```

---

## 🚀 CÁCH 4: CHẠY BẰNG NODE.JS SERVER VỚI PM2 HOẶC SYSTEMD
Tệp `server.mjs` là một HTTP Server độc lập, zero-dependency, tích hợp sẵn nén Gzip và MIME types.

### 4.1. Chạy với PM2:
```bash
npm install -g pm2
cd web
pm2 start server.mjs --name "iro-studio" --env PORT=4173
pm2 save
pm2 startup
```

### 4.2. Chạy dưới dạng Systemd Service (Linux service tự chạy khi bật máy):
File `iro-studio.service` đã được tạo sẵn:
```bash
sudo cp web/iro-studio.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now iro-studio
sudo systemctl status iro-studio
```

---

## 📁 CÁCH 5: NẠP LÊN HOSTING (cPanel / DirectAdmin / CyberPanel / Plesk)
1. Nén toàn bộ các tệp trong thư mục `web/` thành file `.zip`.
2. Mở File Manager của cPanel/CyberPanel, vào thư mục `public_html`.
3. Upload và giải nén (Extract).
4. Tệp `.htaccess` đi kèm đã cấu hình sẵn Gzip, Cache và MIME type `.cube`.
