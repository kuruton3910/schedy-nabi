package com.example.demo.repository;

import com.example.demo.entity.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List; // ★ Listをインポート
import java.util.Optional;
import java.util.UUID;

/**
 * UserCredentialエンティティのためのリポジトリインターフェース。
 * Spring Data JPAが、メソッド名に基づいたDB操作を自動的に実装します。
 */
@Repository // このインターフェースがデータアクセス層のコンポーネントであることを示す
public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {

    /**
     * 大学のIDを指定してユーザー認証情報を検索します。
     * @param universityId 検索する大学ID
     * @return 見つかった場合はUserCredentialを含むOptional、見つからなければ空のOptional
     */
    Optional<UserCredential> findByUniversityId(String universityId);

    /**
     * 暗号化されたパスワードがDBに保存されている（NULLではない）
     * 全てのユーザー認証情報を検索します。
     * SessionRefreshServiceがバックグラウンド更新対象のユーザーを見つけるために使用します。
     * @return パスワードが保存されているUserCredentialのリスト
     */
    List<UserCredential> findByEncryptedPasswordIsNotNull();
}