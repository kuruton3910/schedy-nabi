package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

// Lombokアノテーションでゲッター、セッター、コンストラクタを自動生成
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data // @Getter, @Setter, @ToString, @EqualsAndHashCode などをまとめてくれる
@NoArgsConstructor // 引数なしのコンストラクタ
@AllArgsConstructor // 全ての引数を持つコンストラクタ
@Entity // このクラスがデータベースのテーブルに対応することを示す (JPA)
@Table(name = "user_profiles") // 対応するテーブル名を指定
public class UserCredential {

    @Id // このフィールドが主キーであることを示す
    private UUID id;

    @Column(name = "university_id", unique = true, nullable = false) // READMEにあったので追加
    private String universityId;

    @Column(name = "university_password_encrypted", columnDefinition = "TEXT")
    private String encryptedPassword;

    @Column(name = "session_cookie_encrypted", columnDefinition = "TEXT")
    private String encryptedSessionCookie;
}