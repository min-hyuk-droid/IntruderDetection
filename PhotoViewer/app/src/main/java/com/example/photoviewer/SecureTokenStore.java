package com.example.photoviewer;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.io.IOException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * [보안] 로그인 토큰을 Android Keystore 키(AES-GCM, 기기 밖으로 꺼낼 수 없음)로 암호화해 저장한다.
 * 저장 파일(shared_prefs/secure_tokens.xml)에는 암호문만 남고, 앱(APK)에는 토큰이 들어 있지 않다.
 */
public final class SecureTokenStore {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "photoviewer_token_key";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;

    private final SharedPreferences prefs;

    public SecureTokenStore(Context context) {
        prefs = context.getSharedPreferences("secure_tokens", Context.MODE_PRIVATE);
    }

    private SecretKey key() throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            return (SecretKey) store.getKey(KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }

    /** @return 저장 성공 여부 (실패하면 토큰을 평문으로 남기지 않고 로그인 실패로 처리) */
    public boolean save(String server, String token) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            prefs.edit()
                    .putString(server + "_iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .putString(server + "_token", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .apply();
            return true;
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** @return 복호화한 토큰, 없거나 복호화에 실패하면 "" (다시 로그인) */
    public String load(String server) {
        String iv = prefs.getString(server + "_iv", null);
        String encrypted = prefs.getString(server + "_token", null);
        if (iv == null || encrypted == null) {
            return "";
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, key(),
                    new GCMParameterSpec(TAG_BITS, Base64.decode(iv, Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            e.printStackTrace();
            clear(server);
            return "";
        }
    }

    public void clear(String server) {
        prefs.edit().remove(server + "_iv").remove(server + "_token").apply();
    }
}
