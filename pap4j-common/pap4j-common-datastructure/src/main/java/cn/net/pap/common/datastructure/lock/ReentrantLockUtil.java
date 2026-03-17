package cn.net.pap.common.datastructure.lock;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p><strong>ReentrantLockUtil</strong> 提供了同步令牌（Token）刷新操作的实用工具。</p>
 *
 * <p>此类使用 {@link ReentrantLock} 来确保多个线程不会同时尝试刷新已过期的令牌。</p>
 *
 * <ul>
 *     <li>线程安全的令牌获取。</li>
 *     <li>同步的令牌生成机制。</li>
 * </ul>
 */
public class ReentrantLockUtil {

    private static final ReentrantLock lock = new ReentrantLock();

    /**
     * <p>获取有效的令牌。如果给定的令牌为空或已过期，将触发刷新操作。</p>
     *
     * @param token 当前的 {@link TokenDTO} 令牌。
     * @return 有效的 {@link TokenDTO}。
     */
    public static TokenDTO getToken(TokenDTO token) {
        if (token == null || token.getAccessToken() == null || Instant.now().toEpochMilli() >= token.getExpiresAt()) {
            return synchronizedRefreshToken(token);
        } else {
            return token;
        }
    }

    /**
     * <p>同步刷新令牌，以防止在刷新周期内出现竞态条件。</p>
     *
     * @param token 当前的 {@link TokenDTO} 令牌。
     * @return 新生成且有效的 {@link TokenDTO}。
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

    /**
     * <p>生成新令牌并更新其过期时间。</p>
     *
     * @param token 要更新的令牌实例，若创建新实例则传入 null。
     * @return 更新后的 {@link TokenDTO}。
     */
    private static TokenDTO refreshToken(TokenDTO token) {
        if(token == null) {
            token = new TokenDTO();
        }
        token.setAccessToken(Instant.now().toString());
        token.setExpiresAt(Instant.now().toEpochMilli() + 1);
        return token;
    }

    /**
     * <p><strong>TokenDTO</strong> 封装了访问令牌（Access Token）的数据。</p>
     *
     * <ul>
     *     <li><strong>accessToken：</strong> 表示令牌的字符串。</li>
     *     <li><strong>expiresAt：</strong> 令牌过期的时间戳。</li>
     * </ul>
     */
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
