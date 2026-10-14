# HƯỚNG DẪN TRIỂN KHAI IRO STUDIO 3.0 LÊN MÁY CHỦ (SERVER DEPLOYMENT GUIDE)

Bộ mã nguồn `web/` là phiên bản web độc lập (Standalone Web Studio) được kế thừa và chuyển đổi trọn vẹn từ ứng dụng **IRO Studio 3.0 Android**, bao gồm:
- **Thư viện 392 công thức màu phim** Fujifilm X Weekly (`recipes.json`) với bộ tìm kiếm và lọc cảm biến tức thì.
- **30 mẫu khung ảnh triển lãm (PhotoFrame)** chuẩn bảo tàng và hoài cổ.
- **LightCraft Suite**: Đường cong Tone Curve tương tác Fritsch–Carlson và Bộ 3 bánh xe màu điện ảnh 3-Way Color Wheels (OkLab / OkLCh).
- **Phòng tối quang học 3.0**: Black Pro-Mist, Halation CineStill 800T, Bleach Bypass, Dehaze, Texture, Swirly Bokeh Helios 44-2, 3D Depth Fog, Light Leak 35mm, Hạt muối bạc Dual-Band và Chuyển động Living Film 24fps.
- **Xuất ảnh đa định dạng**: JPEG, WebP, PNG (nhúng ẩn công thức LSB) và xuất bảng màu 3D LUT `.cube` cho DaVinci Resolve, Premiere, CapCut.

---

## CÁCH 1: NẠP LÊN HOSTING (cPanel / DirectAdmin / CyberPanel / Plesk)
Đây là cách nhanh nhất và phổ biến nhất, không yêu cầu cài đặt môi trường.

1. Nén toàn bộ các tệp trong thư mục `web/` thành tệp `web.zip` (hoặc dùng tệp có sẵn `iro-web-production.zip`).
2. Đăng nhập vào trình quản lý hosting (cPanel).
3. Vào **File Manager** -> Mở thư mục gốc trang web: `public_html` (hoặc thư mục tên miền con `subdomain`).
4. Nhấn **Upload** và tải tệp `.zip` lên.
5. Chuột phải vào tệp `.zip` -> chọn **Extract** để giải nén trực tiếp vào thư mục.
6. Tệp `.htaccess` đã được tích hợp sẵn để tự động bật nén Gzip, cấu hình bộ đệm Cache và định dạng MIME cho file `.cube`.
7. Truy cập vào tên miền của bạn để sử dụng ngay!

---

## CÁCH 2: CHẠY TRÊN MÁY CHỦ VPS LINUX VỚI NGINX

### 1. Sao chép mã nguồn lên VPS
```bash
# Trên máy tính của bạn, tải thư mục web lên VPS (thay your-server-ip bằng IP máy chủ)
scp -r web/* root@your-server-ip:/var/www/iro-studio/
```

### 2. Cấu hình Nginx
Mở file cấu hình Nginx:
```bash
sudo nano /etc/nginx/sites-available/iro-studio
```
Dán nội dung cấu hình (đã chuẩn hóa trong tệp `nginx.conf`):
```nginx
server {
    listen 80;
    server_name your-domain.com www.your-domain.com; # Thay bằng tên miền của bạn

    root /var/www/iro-studio;
    index index.html;

    gzip on;
    gzip_vary on;
    gzip_proxied any;
    gzip_comp_level 6;
    gzip_types text/plain text/css application/json application/javascript image/svg+xml;

    types {
        text/plain cube;
        text/javascript js mjs;
        application/json json;
    }

    location / {
        try_files $uri $uri/ /index.html;
        add_header Cache-Control "no-cache";
        add_header Access-Control-Allow-Origin "*";
    }

    location ~* \.(?:css|js|mjs|jpg|jpeg|png|webp|svg|cube)$ {
        expires 30d;
        add_header Cache-Control "public";
    }
}
```

Kích hoạt và khởi động lại Nginx:
```bash
sudo ln -s /etc/nginx/sites-available/iro-studio /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

---

## CÁCH 3: CHẠY BẰNG DOCKER & DOCKER COMPOSE

Dành cho các hệ thống Cloud (AWS EC2, DigitalOcean, Hetzner, Google Cloud, Linode):

### Cách 3.1: Dùng Docker Compose (1 lệnh duy nhất)
Trong thư mục `web/`, chạy:
```bash
docker compose up -d --build
```
Ứng dụng sẽ tự động được đóng gói thành container Nginx Alpine siêu nhẹ (~25MB) và chạy tại cổng `8080`. Bạn truy cập `http://your-server-ip:8080`.

### Cách 3.2: Dùng lệnh Docker chuẩn
```bash
docker build -t iro-studio:3.0 .
docker run -d -p 80:80 --name iro-web --restart always iro-studio:3.0
```

---

## CÁCH 4: CHẠY BẰNG NODE.JS SERVER VỚI PM2

Tệp `server.mjs` đi kèm là một HTTP Server hoàn toàn độc lập, không phụ thuộc vào bất kỳ thư viện ngoài nào (Zero Dependency).

### 1. Kiểm tra chạy thử
```bash
node server.mjs
```
Mặc định server sẽ lắng nghe tại `http://localhost:4173`. Bạn có thể đổi cổng bằng biến môi trường `PORT`:
```bash
PORT=3000 node server.mjs
```

### 2. Chạy ngầm vĩnh viễn với PM2 (Production)
```bash
npm install -g pm2
pm2 start server.mjs --name "iro-studio" --env PORT=4173
pm2 save
pm2 startup
```

---

## CÁCH 5: TRIỂN KHAI MIỄN PHÍ LÊN CLOUD (VERCEL / NETLIFY / GITHUB PAGES)

Do IRO Studio Web là ứng dụng thuần Client-side hiện đại (HTML5, ES Modules, Canvas 2D & Web Workers):

### 1. Vercel
- Cài Vercel CLI: `npm i -g vercel`
- Vào thư mục `web/` và gõ: `vercel --prod`

### 2. Netlify
- Kéo thả thư mục `web/` vào giao diện **Netlify Drop** (`https://app.netlify.com/drop`). Trang web sẽ trực tuyến sau 5 giây.

### 3. GitHub Pages
- Tạo nhánh `gh-pages` hoặc vào Settings kho lưu trữ GitHub -> Pages -> Chọn thư mục `/web` hoặc `/dist`.
