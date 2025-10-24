# ----- 1. ビルド用ステージ -----
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

# 先に Maven Wrapper と pom.xml をコピーする
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Maven Wrapper に実行権限を与える
RUN chmod +x ./mvnw

# 依存関係をダウンロード（キャッシュを効かせるため）
RUN ./mvnw dependency:go-offline

# プロジェクトの全ソースコードをコピー
COPY src ./src

# ★★★ アプリケーションをビルド（テストはスキップ） ★★★
RUN ./mvnw package -DskipTests


# ----- 2. 実行用ステージ -----
FROM eclipse-temurin:17-jre
WORKDIR /app

# ★★★ Chromeインストール部分を修正 ★★★
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    apt-transport-https \
    ca-certificates \
    curl \
    gnupg \
    # --- Chrome Core Dependencies ---
    libasound2t64 \ 
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
    # --- Other Recommended Dependencies ---
    fonts-liberation \
    libappindicator3-1 \
    libdrm2 \
    # libgbm1 \ <-- 重複しているので削除してもOK
    libu2f-udev \
    lsb-release \
    udev \
    wget \
    xdg-utils && \
    # (以降のChromeリポジトリ追加、インストール、確認コマンドは変更なし)
    curl -fsSL https://dl-ssl.google.com/linux/linux_signing_key.pub | gpg --dearmor -o /usr/share/keyrings/google-chrome-keyring.gpg && \
    sh -c 'echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-chrome-keyring.gpg] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list' && \
    apt-get update && \
    apt-get install -y --no-install-recommends google-chrome-stable && \
    google-chrome-stable --version && \
    apt-get purge -y --auto-remove curl gnupg wget && \
    rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]