# IRO Studio 3.0 — High-performance Alpine Nginx Container
FROM nginx:alpine

# Cài curl để phục vụ Healthcheck cho Docker & Docker Compose
RUN apk add --no-cache curl

# Dọn dẹp thư mục html mặc định của nginx
RUN rm -rf /usr/share/nginx/html/*

# Cấu hình Nginx tối ưu cho IRO Studio (MIME types, Gzip, Caching)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Sao chép toàn bộ source web vào thư mục phục vụ của Nginx
COPY . /usr/share/nginx/html/

# Dọn dẹp các tệp cấu hình không cần thiết trong public web
RUN rm -f /usr/share/nginx/html/Dockerfile \
          /usr/share/nginx/html/docker-compose.yml \
          /usr/share/nginx/html/nginx.conf \
          /usr/share/nginx/html/DEPLOY.md \
          /usr/share/nginx/html/README.md \
          /usr/share/nginx/html/.dockerignore

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -f http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
