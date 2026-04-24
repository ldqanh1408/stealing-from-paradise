FROM nginx:alpine

RUN apk add --no-cache bash

RUN echo 'Service unavailable' > /usr/share/nginx/html/50x.html

COPY reverse-proxy.conf /etc/nginx/templates/default.conf.template

EXPOSE 80

CMD ["sh", "-c", "envsubst < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf && nginx -g 'daemon off;'"]
