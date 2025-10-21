# ----- 1. ビルド用ステージ -----
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# ★★★ この行を追加 ★★★
# Maven Wrapperに実行権限を与える
RUN chmod +x ./mvnw

# 依存関係をダウンロード（キャッシュを効かせるため）
RUN ./mvnw dependency:go-offline

# プロジェクトの全ソースコードをコピー
COPY src ./src

# アプリケーションをビルド（テストはスキップ）
# (↑で権限付与したので、ここにあった chmod は削除してOKです)
RUN ./mvnw package -DskipTests


# ----- 2. 実行用ステージ -----
# (ここは変更ありません)
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]