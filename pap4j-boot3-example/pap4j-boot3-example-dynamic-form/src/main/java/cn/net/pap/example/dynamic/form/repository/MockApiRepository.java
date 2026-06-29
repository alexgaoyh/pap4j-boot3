package cn.net.pap.example.dynamic.form.repository;

import cn.net.pap.example.dynamic.form.entity.MockApi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Mock API 数据访问仓储接口。
 */
public interface MockApiRepository extends JpaRepository<MockApi, Long> {

    /**
     * 根据 URL 和支持的方法列表查找候选 Mock 配置
     *
     * @param url     相对路径
     * @param methods 候选的 HTTP 方法 (如 POST, ANY, *)
     * @return 候选配置列表
     */
    List<MockApi> findByUrlAndMethodIn(String url, Collection<String> methods);
}
