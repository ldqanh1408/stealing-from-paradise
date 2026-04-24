FROM nginx:alpine

RUN apk add --no-cache bash \
 && echo 'Service unavailable' > /usr/share/nginx/html/50x.html \
 && chmod +x /docker-entrypoint.sh

COPY docker-entrypoint.sh /docker-entrypoint.sh

EXPOSE 80

ENTRYPOINT ["/docker-entrypoint.sh"]
