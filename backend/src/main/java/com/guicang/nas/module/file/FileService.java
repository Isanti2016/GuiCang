package com.guicang.nas.module.file;

import com.guicang.nas.infra.storage.FileEntry;
import com.guicang.nas.module.file.dto.FileStreamInfo;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/** 文件管理服务：目录操作 + 上传下载（含目录级权限校验与审计）。 */
public interface FileService {

  /** 目录列表（需 READ 权限）。 */
  List<FileEntry> list(String path);

  /** 新建目录（需 WRITE 权限）。 */
  void mkdir(String path);

  /** 重命名（需 WRITE 权限）。 */
  void rename(String path, String newName);

  /** 移动（源需 WRITE，目标目录需 WRITE）。 */
  void move(String path, String target);

  /** 删除（需 WRITE 权限；目录默认须为空，recursive 时递归删除）。 */
  void delete(String path, boolean recursive);

  /** 上传文件到目录（需 WRITE 权限；校验大小与扩展名；流式写盘 + 原子改名）。 */
  FileEntry upload(String dirPath, MultipartFile file);

  /** 文件流信息（需 READ 权限；用于下载/预览 Range 响应）。 */
  FileStreamInfo stream(String path);

  /** 读取文本内容（需 READ 权限；限大小）。 */
  String readText(String path);

  /** 保存文本内容（需 WRITE 权限；仅允许 md/txt/markdown 扩展名）。 */
  void writeText(String path, String content);

  /** 图片缩略图（需 READ 权限；懒生成 + 磁盘缓存，返回缩略图流信息）。 */
  FileStreamInfo thumbnail(String path);

  /** 按名称/路径关键字搜索（索引查询 + 权限过滤）。 */
  List<FileEntry> search(String keyword);
}
