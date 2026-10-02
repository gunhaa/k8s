# root가 아닌 사용자로 8080 포트에서 동작하는 nginx 이미지
FROM nginxinc/nginx-unprivileged:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY index.html app.js config.js style.css /usr/share/nginx/html/
EXPOSE 8080
