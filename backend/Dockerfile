# ----- 1. ビルド用ステージ -----
FROM eclipse-temurin:17-jdk AS build
# ... (ビルドステージは変更なし) ...
RUN ./mvnw package -DskipTests


# ----- 2. 実行用ステージ -----
FROM eclipse-temurin:17-jre
WORKDIR /app

# ★★★ ここからChromeインストールコマンドを追加 ★★★
# 必要なライブラリとwgetをインストール
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    gnupg \
    ca-certificates \
    fonts-liberation \
    libappindicator3-1 \
    libasound2 \
    libatk-bridge2.0-0 \
    libatk1.0-0 \
    libc6 \
    libcairo2 \
    libcups2 \
    libdbus-1-3 \
    libexpat1 \
    libfontconfig1 \
    libgbm1 \
    libgcc1 \
    libglib2.0-0 \
    libgtk-3-0 \
    libnspr4 \
    libnss3 \
    libpango-1.0-0 \
    libpangocairo-1.0-0 \
    libstdc++6 \
    libx11-6 \
    libx11-xcb1 \
    libxcb1 \
    libxcomposite1 \
    libxcursor1 \
    libxdamage1 \
    libxext6 \
    libxfixes3 \
    libxi6 \
    libxrandr2 \
    libxrender1 \
    libxss1 \
    libxtst6 \
    lsb-release \
    xdg-utils

# Google Chromeの公式リポジトリキーを追加
RUN wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add -

# Google Chromeのリポジトリを追加
RUN sh -c 'echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google-chrome.list'

# Google Chrome (stable) をインストール
RUN apt-get update && apt-get install -y --no-install-recommends \
    google-chrome-stable

# 不要なファイルを削除してイメージサイズを削減 (wget と gnupg の削除)
RUN apt-get purge -y --auto-remove wget gnupg && \
    rm -rf /var/lib/apt/lists/*
# ★★★ Chromeインストールここまで ★★★

COPY --from=build /app/target/*.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]