# ----- 1. ビルド用ステージ -----
# Java 17 (JDK) をベースイメージとして使用
FROM eclipse-temurin:17-jdk AS build

# 作業ディレクトリを設定
WORKDIR /app

# Maven Wrapperとプロジェクト定義ファイルを先にコピー
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# 依存関係をダウンロード（キャッシュを効かせるため）
RUN ./mvnw dependency:go-offline

# プロジェクトの全ソースコードをコピー
COPY src ./src

# Maven Wrapperに実行権限を与える
RUN chmod +x ./mvnw

# アプリケーションをビルド（テストはスキップ）
RUN ./mvnw package -DskipTests


# ----- 2. 実行用ステージ -----
# Java 17 (JRE) のみをベースイメージとして使用（イメージを軽量化するため）
FROM eclipse-temurin:17-jre

WORKDIR /app

# ビルドステージから生成されたJARファイルのみをコピー
COPY --from=build /app/target/*.jar /app.jar

# Spring Bootアプリが使用するポート（デフォルトは8080）
EXPOSE 8080

# コンテナ起動時にJavaアプリを実行
ENTRYPOINT ["java","-jar","/app.jar"]