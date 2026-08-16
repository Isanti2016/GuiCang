package com.guicang.nas.module.file;

import com.guicang.nas.infra.storage.FileEntry;
import java.util.List;

/** 文件管理服务：目录操作（含目录级权限校验与审计）。 */
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
}
