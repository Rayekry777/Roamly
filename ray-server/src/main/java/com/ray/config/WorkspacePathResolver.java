package com.ray.config;

import java.nio.file.Files;
import java.nio.file.Path;

/** 将本地开发相对路径稳定解析到仓库根目录，避免模块工作目录产生重复文件夹。 */
public final class WorkspacePathResolver {
    private WorkspacePathResolver() {}

    /** 解析配置路径；绝对路径保持不变，从 ray-server 启动时自动回到父级仓库目录。 */
    public static Path resolve(String configuredPath) {
        Path configured = Path.of(configuredPath);
        if (configured.isAbsolute()) return configured.normalize();

        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path repositoryRoot = repositoryRoot(workingDirectory);
        return repositoryRoot.resolve(configured).normalize();
    }

    private static Path repositoryRoot(Path workingDirectory) {
        Path name = workingDirectory.getFileName();
        Path parent = workingDirectory.getParent();
        if (name != null
                && "ray-server".equals(name.toString())
                && parent != null
                && Files.isRegularFile(parent.resolve("pom.xml"))
                && Files.isDirectory(parent.resolve("ray-server"))) {
            return parent;
        }
        return workingDirectory;
    }
}
