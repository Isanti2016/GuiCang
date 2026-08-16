package com.guicang.nas.module.sync;

/** 同步源抽象（为二期扩展预留：WebDAV/手机上传/监控目录等实现本接口）。 */
public interface SyncSource {

  /**
   * 扫描存储根下相对目录，与 file_index 对比产生增量。
   *
   * @param relativePath 存储根下相对路径（空串表示全根）
   * @return 增量变更集
   */
  ChangeSet scan(String relativePath);
}
