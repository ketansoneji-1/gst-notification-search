# Run with: docker run -d --network host --cap-add NET_ADMIN --env-file server.env -v meshlink:/var/lib/meshlink meshlink
FROM python:3.12-slim
RUN apt-get update && apt-get install -y --no-install-recommends wireguard-tools iptables iproute2 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /opt/meshlink
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY server ./server
ENV MESHLINK_DB=/var/lib/meshlink/meshlink.db MESHLINK_HUB_APPLY=1
EXPOSE 8443 51820/udp
CMD ["uvicorn", "--factory", "server.main:create_app", "--host", "0.0.0.0", "--port", "8443", "--proxy-headers"]
