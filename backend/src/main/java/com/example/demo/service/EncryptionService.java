package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES/GCM方式で文字列の暗号化・復号を行う責務を持つServiceクラス。
 */
@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // in bits
    private static final int IV_LENGTH = 12; // in bytes
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKeySpec secretKey;

    // コンストラクタでapplication.propertiesからマスターキーを読み込み、SecretKeySpecを生成
    public EncryptionService(@Value("${SECURITY_MASTER_KEY}") String masterKey) {
        this.secretKey = new SecretKeySpec(masterKey.getBytes(StandardCharsets.UTF_8), "AES");
    }

    public String encrypt(String valueToEnc) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(valueToEnc.getBytes(StandardCharsets.UTF_8));

            // IV（初期化ベクトル）と暗号文を結合して保存する
            byte[] payload = new byte[IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, payload, 0, IV_LENGTH);
            System.arraycopy(cipherText, 0, payload, IV_LENGTH, cipherText.length);

            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new RuntimeException("暗号化に失敗しました", e);
        }
    }

    public String decrypt(String encryptedValue) {
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedValue);
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("不正な暗号データです。");
            }

            // IVと暗号文を分離
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] decryptedText = cipher.doFinal(cipherText);
            return new String(decryptedText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("復号に失敗しました", e);
        }
    }
}