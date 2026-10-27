package cn.net.pap.common.file.dto;

import java.time.Instant;

/**
 * 文件引用记录。
 *
 * <p>filePath 是唯一真相（形如 {@code fs2:8e/fc/8efc1f3c9a5b4d2e8f60a8b1c3d5e7f9}），其余字段均为附属信息。
 * 它由「存储实例名 + ':' + 相对位置」组成：实例名到实际根目录或对象存储桶的映射在配置里，
 * 相对位置才是具体存储上的落点，使用前必须先按 ':' 拆开。</p>
 *
 * <p><b>注意：</b>filePath 是一个存储定位符（locator / storage key），<b>不是</b>操作系统物理路径。
 * 不要直接交给 {@code new File(...)} 或 {@code Path.of(...)}：冒号在 Windows 上是非法字符，
 * 在其它平台也会被当成相对路径。物理路径 = 实例对应的存储根目录 + 去掉实例前缀后的相对位置。</p>
 *
 * @param id        记录主键，由应用生成
 * @param bizId     业务键，不透明标签
 * @param filePath  存储定位符，形如 {@code fs2:8e/fc/8efc1f3c9a5b4d2e8f60a8b1c3d5e7f9}
 * @param size      字节数
 * @param origName  原始文件名，永不参与路径计算
 * @param createdAt 归档时间
 * @param updatedAt 最近变更时间
 * @param deletedAt 软删时间，{@code null} 表示未删除
 */
public record FileDTO(
        long id,
        String bizId,
        String filePath,
        long size,
        String origName,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {
}
