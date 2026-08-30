package com.guicang.nas.module.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 文件索引服务实现：操作时增量维护 + 名称搜索。 */
@Service
public class FileIndexServiceImpl implements FileIndexService {

  private final FileIndexMapper fileIndexMapper;

  public FileIndexServiceImpl(FileIndexMapper fileIndexMapper) {
    this.fileIndexMapper = fileIndexMapper;
  }

  /**
   * 新增/更新索引（path 唯一，存在则更新）。
   *
   * @param relativePath 文件相对路径
   * @param name 文件名
   * @param kind 文件类型（dir/image/video/note/other）
   * @param size 文件大小（字节，目录为 null）
   * @param mtime 修改时间（毫秒时间戳，可空）
   * @param owner 属主用户名
   */
  @Override
  @Transactional
  public void upsert(
      String relativePath, String name, String kind, Long size, Long mtime, String owner) {
    FileIndex existing = findByPath(relativePath);
    if (existing != null) {
      existing.setName(name);
      existing.setKind(kind);
      existing.setSize(size);
      existing.setMtime(mtime);
      existing.setOwner(owner);
      existing.setIndexedAt(System.currentTimeMillis());
      fileIndexMapper.updateById(existing);
      return;
    }
    FileIndex index = new FileIndex();
    index.setPath(relativePath);
    index.setName(name);
    int dot = name.lastIndexOf('.');
    index.setExt(dot >= 0 ? name.substring(dot + 1).toLowerCase() : null);
    index.setKind(kind);
    index.setSize(size);
    index.setMtime(mtime);
    index.setOwner(owner);
    index.setIndexedAt(System.currentTimeMillis());
    fileIndexMapper.insert(index);
  }

  /**
   * 删除索引（目录删除时连同子项索引一并移除）。
   *
   * @param relativePath 文件相对路径
   */
  @Override
  @Transactional
  public void remove(String relativePath) {
    // 目录删除时连同子项索引一并移除
    fileIndexMapper.delete(
        new LambdaQueryWrapper<FileIndex>().eq(FileIndex::getPath, relativePath));
    fileIndexMapper.delete(
        new LambdaQueryWrapper<FileIndex>().likeRight(FileIndex::getPath, relativePath + "/"));
  }

  /**
   * 重命名/移动后更新路径（目录时其下所有子项路径一并迁移）。
   *
   * @param oldPath 旧路径
   * @param newPath 新路径
   */
  @Override
  @Transactional
  public void rename(String oldPath, String newPath) {
    FileIndex existing = findByPath(oldPath);
    if (existing == null) {
      return;
    }
    // 若旧路径是目录，其下所有子项路径一并迁移
    if (existing.getKind().equals("dir")) {
      List<FileIndex> children =
          fileIndexMapper.selectList(
              new LambdaQueryWrapper<FileIndex>().likeRight(FileIndex::getPath, oldPath + "/"));
      children.forEach(
          child -> {
            child.setPath(newPath + child.getPath().substring(oldPath.length()));
            fileIndexMapper.updateById(child);
          });
    }
    existing.setPath(newPath);
    int dot = newPath.lastIndexOf('/');
    existing.setName(dot >= 0 ? newPath.substring(dot + 1) : newPath);
    fileIndexMapper.updateById(existing);
  }

  /**
   * 路径是否存在索引。
   *
   * @param relativePath 相对路径
   * @return 存在返回 true
   */
  @Override
  public boolean exists(String relativePath) {
    return findByPath(relativePath) != null;
  }

  /**
   * 按名称关键字搜索（LIKE 匹配 name 与 path）。
   *
   * @param keyword 搜索关键字
   * @return 匹配的索引列表
   */
  @Override
  public List<FileIndex> search(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return List.of();
    }
    return fileIndexMapper.selectList(
        new LambdaQueryWrapper<FileIndex>()
            .like(FileIndex::getPath, keyword)
            .or()
            .like(FileIndex::getName, keyword)
            .orderByAsc(FileIndex::getPath));
  }

  /**
   * 按路径前缀查询（含前缀自身，用于目录扫描对比）。
   *
   * @param prefix 路径前缀（空则返回全部）
   * @return 匹配的索引列表
   */
  @Override
  public List<FileIndex> listByPrefix(String prefix) {
    if (prefix == null || prefix.isBlank()) {
      return fileIndexMapper.selectList(null);
    }
    return fileIndexMapper.selectList(
        new LambdaQueryWrapper<FileIndex>()
            .eq(FileIndex::getPath, prefix)
            .or()
            .likeRight(FileIndex::getPath, prefix + "/"));
  }

  private FileIndex findByPath(String relativePath) {
    return fileIndexMapper.selectOne(
        new LambdaQueryWrapper<FileIndex>().eq(FileIndex::getPath, relativePath));
  }
}
