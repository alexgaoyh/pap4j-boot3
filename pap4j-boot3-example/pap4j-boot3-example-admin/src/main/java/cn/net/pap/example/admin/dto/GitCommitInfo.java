package cn.net.pap.example.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Schema(description = "Git 提交与构建元数据信息")
public class GitCommitInfo {

    @Schema(description = "Git 标签", example = "v0.0.3")
    private String tags;                    // git.tags
    @Schema(description = "当前分支名", example = "main")
    private String branch;                  // git.branch
    @Schema(description = "本地代码是否有未提交修改", example = "false")
    private String dirty;                   // git.dirty
    @Schema(description = "远程仓库 Origin 地址", example = "https://gitee.com/...git")
    private String remoteOriginUrl;         // git.remote.origin.url
    @Schema(description = "提交 Commit ID", example = "6dedcbce34d2164a68086bb5703d65b06177d409")
    private String commitId;                // git.commit.id
    @Schema(description = "简写 Commit ID", example = "6dedcbc")
    private String commitIdAbbrev;          // git.commit.id.abbrev
    @Schema(description = "提交描述", example = "v0.0.3-164-g6dedcbc")
    private String describe;                // git.commit.id.describe
    @Schema(description = "简写提交描述", example = "v0.0.3-164-g6dedcbc")
    private String describeShort;           // git.commit.id.describe-short
    @Schema(description = "提交者用户名", example = "alexgaoyh")
    private String commitUserName;          // git.commit.user.name
    @Schema(description = "提交者邮箱", example = "alexgaoyh@sina.com")
    private String commitUserEmail;         // git.commit.user.email
    @Schema(description = "完整提交信息", example = "feat: add swaggerui.")
    private String commitMessageFull;       // git.commit.message.full
    @Schema(description = "简写提交信息", example = "feat: add swaggerui.")
    private String commitMessageShort;      // git.commit.message.short
    @Schema(description = "提交时间描述", example = "2026-06-23T11:01:32+08:00")
    private String commitTime;              // git.commit.time
    @Schema(description = "提交时间 (ISO 格式)", example = "2026-06-23T11:01:32+08:00")
    private String commitTimeIso;           // git.commit.time.iso
    @Schema(description = "项目构建版本号", example = "0.0.3")
    private String buildVersion;            // git.build.version
    @Schema(description = "构建时间描述", example = "2026-06-23T11:47:24+08:00")
    private String buildTime;               // git.build.time
    @Schema(description = "构建时间 (ISO 格式)", example = "2026-06-23T11:47:24+08:00")
    private String buildTimeIso;            // git.build.time.iso
    @Schema(description = "构建主机名称", example = "alexgaoyh")
    private String buildHost;               // git.build.host
    @Schema(description = "构建执行者用户名", example = "alexgaoyh")
    private String buildUserName;           // git.build.user.name
    @Schema(description = "构建执行者邮箱", example = "alexgaoyh@sina.com")
    private String buildUserEmail;          // git.build.user.email
    @Schema(description = "最近的标签名", example = "v0.0.3")
    private String closestTagName;          // git.closest.tag.name
    @Schema(description = "距离最近标签的提交数", example = "164")
    private String closestTagCommitCount;   // git.closest.tag.commit.count
    @Schema(description = "总提交数", example = "1412")
    private String totalCommitCount;        // git.total.commit.count

    public GitCommitInfo() {
    }

    // Constructor that populates from Properties
    public GitCommitInfo(Properties properties) {
        this.tags = properties.getProperty("git.tags", "");
        this.branch = properties.getProperty("git.branch", "");
        this.dirty = properties.getProperty("git.dirty", "");
        this.remoteOriginUrl = properties.getProperty("git.remote.origin.url", "");
        this.commitId = properties.getProperty("git.commit.id", "");
        this.commitIdAbbrev = properties.getProperty("git.commit.id.abbrev", "");
        this.describe = properties.getProperty("git.commit.id.describe", "");
        this.describeShort = properties.getProperty("git.commit.id.describe-short", "");
        this.commitUserName = properties.getProperty("git.commit.user.name", "");
        this.commitUserEmail = properties.getProperty("git.commit.user.email", "");
        this.commitMessageFull = properties.getProperty("git.commit.message.full", "");
        this.commitMessageShort = properties.getProperty("git.commit.message.short", "");
        this.commitTime = properties.getProperty("git.commit.time", "");
        this.commitTimeIso = properties.getProperty("git.commit.time.iso", "");
        this.buildVersion = properties.getProperty("git.build.version", "");
        this.buildTime = properties.getProperty("git.build.time", "");
        this.buildTimeIso = properties.getProperty("git.build.time.iso", "");
        this.buildHost = properties.getProperty("git.build.host", "");
        this.buildUserName = properties.getProperty("git.build.user.name", "");
        this.buildUserEmail = properties.getProperty("git.build.user.email", "");
        this.closestTagName = properties.getProperty("git.closest.tag.name", "");
        this.closestTagCommitCount = properties.getProperty("git.closest.tag.commit.count", "");
        this.totalCommitCount = properties.getProperty("git.total.commit.count", "");
    }

    public static GitCommitInfo loadFromProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = GitCommitInfo.class.getClassLoader().getResourceAsStream("git.properties")) {
            if (input == null) {
                throw new IOException("git.properties file not found in classpath");
            }
            properties.load(input);
        }
        return new GitCommitInfo(properties);
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getDirty() {
        return dirty;
    }

    public void setDirty(String dirty) {
        this.dirty = dirty;
    }

    public String getRemoteOriginUrl() {
        return remoteOriginUrl;
    }

    public void setRemoteOriginUrl(String remoteOriginUrl) {
        this.remoteOriginUrl = remoteOriginUrl;
    }

    public String getCommitId() {
        return commitId;
    }

    public void setCommitId(String commitId) {
        this.commitId = commitId;
    }

    public String getCommitIdAbbrev() {
        return commitIdAbbrev;
    }

    public void setCommitIdAbbrev(String commitIdAbbrev) {
        this.commitIdAbbrev = commitIdAbbrev;
    }

    public String getDescribe() {
        return describe;
    }

    public void setDescribe(String describe) {
        this.describe = describe;
    }

    public String getDescribeShort() {
        return describeShort;
    }

    public void setDescribeShort(String describeShort) {
        this.describeShort = describeShort;
    }

    public String getCommitUserName() {
        return commitUserName;
    }

    public void setCommitUserName(String commitUserName) {
        this.commitUserName = commitUserName;
    }

    public String getCommitUserEmail() {
        return commitUserEmail;
    }

    public void setCommitUserEmail(String commitUserEmail) {
        this.commitUserEmail = commitUserEmail;
    }

    public String getCommitMessageFull() {
        return commitMessageFull;
    }

    public void setCommitMessageFull(String commitMessageFull) {
        this.commitMessageFull = commitMessageFull;
    }

    public String getCommitMessageShort() {
        return commitMessageShort;
    }

    public void setCommitMessageShort(String commitMessageShort) {
        this.commitMessageShort = commitMessageShort;
    }

    public String getCommitTime() {
        return commitTime;
    }

    public void setCommitTime(String commitTime) {
        this.commitTime = commitTime;
    }

    public String getCommitTimeIso() {
        return commitTimeIso;
    }

    public void setCommitTimeIso(String commitTimeIso) {
        this.commitTimeIso = commitTimeIso;
    }

    public String getBuildVersion() {
        return buildVersion;
    }

    public void setBuildVersion(String buildVersion) {
        this.buildVersion = buildVersion;
    }

    public String getBuildTime() {
        return buildTime;
    }

    public void setBuildTime(String buildTime) {
        this.buildTime = buildTime;
    }

    public String getBuildTimeIso() {
        return buildTimeIso;
    }

    public void setBuildTimeIso(String buildTimeIso) {
        this.buildTimeIso = buildTimeIso;
    }

    public String getBuildHost() {
        return buildHost;
    }

    public void setBuildHost(String buildHost) {
        this.buildHost = buildHost;
    }

    public String getBuildUserName() {
        return buildUserName;
    }

    public void setBuildUserName(String buildUserName) {
        this.buildUserName = buildUserName;
    }

    public String getBuildUserEmail() {
        return buildUserEmail;
    }

    public void setBuildUserEmail(String buildUserEmail) {
        this.buildUserEmail = buildUserEmail;
    }

    public String getClosestTagName() {
        return closestTagName;
    }

    public void setClosestTagName(String closestTagName) {
        this.closestTagName = closestTagName;
    }

    public String getClosestTagCommitCount() {
        return closestTagCommitCount;
    }

    public void setClosestTagCommitCount(String closestTagCommitCount) {
        this.closestTagCommitCount = closestTagCommitCount;
    }

    public String getTotalCommitCount() {
        return totalCommitCount;
    }

    public void setTotalCommitCount(String totalCommitCount) {
        this.totalCommitCount = totalCommitCount;
    }
}
