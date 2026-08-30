package com.guicang.nas.module.file;

import com.guicang.nas.infra.storage.FileEntry;
import com.guicang.nas.module.file.dto.DuplicateGroup;
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

  /** 删除（软删除：移入回收站，可恢复；需 WRITE 权限）。 */
  void delete(String path, boolean recursive);

  /** 回收站列表（管理员可见全部，普通用户仅本人）。 */
  List<TrashItem> listTrash();

  /** 恢复回收站条目到原路径（原位置被占用时提示）。 */
  void restoreTrash(Long id);

  /** 彻底删除回收站条目（不可恢复）。 */
  void purgeTrash(Long id);

  /** 清空回收站（管理员或本人）。 */
  void emptyTrash();

  /** 自动清空过期回收站条目（定时任务调用，无需登录；days=0 表示不清理）。 */
  int purgeExpiredTrash(int days);

  /** 批量软删除文件/目录（移入回收站，需 WRITE 权限）。 */
  void deleteBatch(List<String> paths, boolean recursive);

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

  /** 全文检索（匹配 md/txt 内容 + 权限过滤）。 */
  List<FileEntry> searchContent(String keyword);

  /** 查找重复文件（相同大小 + 相同 SHA-256）。 */
  List<DuplicateGroup> findDuplicates(String path);

  /** 某文件的历史版本列表（倒序）。 */
  List<FileVersion> listVersions(String path);

  /** 回滚到指定历史版本。 */
  void restoreVersion(Long id);

  /** 递归收集目录下图片/视频（相册数据源；需 READ 权限；限制深度与数量）。 */
  /** 批量打包下载（zip；需 READ 权限）。 */
  FileStreamInfo zipDownload(List<String> paths);

  /** 递归收集目录下图片/视频（相册数据源；需 READ 权限；限制深度与数量）。 */
  List<FileEntry> media(String path);
}
