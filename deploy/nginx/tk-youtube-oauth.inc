# Include inside the TK server block so credentials never enter proxy logs.
location ^~ /uploads/open-api-youtube/ { return 404; }

location ~ "^/admin-api/tk/(channel-publish/youtube/callback|open/v1/youtube/auth/(callback|sessions/ytauth_[0-9a-f]{32}/launch))$" {
    access_log off;
    error_log /dev/null;
    proxy_pass http://127.0.0.1:48080;
    proxy_http_version 1.1;
    proxy_set_header Host $host:$server_port;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 90s;
}
