package cn.net.pap.example.admin.dto;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * git-commit-id-maven-plugin
 */
public class GitCommitInfo {

    private String tags;                    // git.tags
    private String branch;                  // git.branch
    private String dirty;                   // git.dirty
    private String remoteOriginUrl;         // git.remote.origin.url
    private String commitId;                // git.commit.id
    private String commitIdAbbrev;          // git.commit.id.abbrev
    private String describe;                // git.commit.id.describe
    private String describeShort;           // git.commit.id.describe-short
    private String commitUserName;          // git.commit.user.name
    private String commitUserEmail;         // git.commit.user.email
    private String commitMessageFull;       // git.commit.message.full
    private String commitMessageShort;      // git.commit.message.short
    private String commitTime;              // git.commit.time
    private String commitTimeIso;           // git.commit.time.iso
    private String buildVersion;            // git.build.version
    private String buildTime;               // git.build.time
    private String buildTimeIso;            // git.build.time.iso
    private String buildHost;               // git.build.host
    private String buildUserName;           // git.build.user.name
    private String buildUserEmail;          // git.build.user.email
    private String closestTagName;          // git.closest.tag.name
    private String closestTagCommitCount;   // git.closest.tag.commit.count
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
