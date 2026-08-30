package com.guicang.nas.module.share;

import com.guicang.nas.module.file.dto.FileStreamInfo;
import com.guicang.nas.module.share.dto.ShareVO;
import java.util.List;

/** 分享链接服务。 */
public interface ShareService {

  /** 创建分享链接（可选密码与过期天数）。 */
  ShareVO create(String path, String password, Integer expireDays);

  /** 列出我的分享。 */
  List<ShareVO> list();

  /** 撤销分享。 */
  void revoke(String token);

  /** 解析分享（校验存在/过期/密码），返回分享条目。 */
  Share resolve(String token, String password);

  /** 下载分享内容（文件直接下，目录打包 zip；免登录）。 */
  FileStreamInfo download(String token, String password);
}
