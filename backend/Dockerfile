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

# (Chromeインストール部分はそのまま)
# 必要なライブラリとwgetをインストール
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    gnupg \
    # ... (長いリストは省略) ...
    xdg-utils
# Google Chromeの公式リポジトリキーを追加
RUN wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add -
# Google Chromeのリポジトリを追加
RUN sh -c 'echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google-chrome.list'
# Google Chrome (stable) をインストール
RUN apt-get update && apt-get install -y --no-install-recommends \
    google-chrome-stable
# 不要なファイルを削除してイメージサイズを削減
RUN apt-get purge -y --auto-remove wget gnupg && \
    rm -rf /var/lib/apt/lists/*

# ビルドステージから生成されたJARファイルのみをコピー
COPY --from=build /app/target/*.jar /app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]