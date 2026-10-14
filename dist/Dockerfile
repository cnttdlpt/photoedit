# IRO Studio 3.0 — High-performance Alpine Nginx Container
FROM nginx:alpine

# Sao chép toàn bộ source web vào thư mục gốc của Nginx
COPY . /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
