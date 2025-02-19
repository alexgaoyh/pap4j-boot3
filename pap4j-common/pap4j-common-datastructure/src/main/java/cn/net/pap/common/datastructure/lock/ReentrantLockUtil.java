package cn.net.pap.common.datastructure.lock;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

public class ReentrantLockUtil {

    private static final ReentrantLock lock = new ReentrantLock();

    public static TokenDTO getToken(TokenDTO token) {
        if (token == null || token.getAccessToken() == null || Instant.now().toEpochMilli() >= token.getExpiresAt()) {
            return synchronizedRefreshToken(token);
        } else {
            return token;
        }
    }

    /**
     * 同步刷新令牌的方法
     */
    private static TokenDTO synchronizedRefreshToken(TokenDTO token) {
        lock.lock();
        try {
            if (token == null || token.getAccessToken() == null || Instant.now().toEpochMilli() >= token.getExpiresAt()) {
                return refreshToken(token);
            } else {
                return token;
            }
        } finally {
            lock.unlock();
        }
    }

    private static TokenDTO refreshToken(TokenDTO token) {
        if(token == null) {
            token = new TokenDTO();
        }
        token.setAccessToken(Instant.now().toString());
        token.setExpiresAt(Instant.now().toEpochMilli() + 1);
        return token;
    }

    static class TokenDTO {
        private String accessToken;
        private long expiresAt;

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public long getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(long expiresAt) {
            this.expiresAt = expiresAt;
        }

        @Override
        public String toString() {
            return "TokenDTO{" +
                    "accessToken='" + accessToken + '\'' +
                    ", expiresAt=" + expiresAt +
                    '}';
        }
    }

}
