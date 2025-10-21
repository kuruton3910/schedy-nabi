package com.example.demo.repository;

import com.example.demo.entity.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository // このインターフェースがデータアクセス層のコンポーネントであることを示す
public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {

    // 大学のIDを指定してユーザー認証情報を検索するためのカスタムメソッド
    // Spring Data JPAがメソッド名から自動的にSQLを生成してくれる
    Optional<UserCredential> findByUniversityId(String universityId);
}