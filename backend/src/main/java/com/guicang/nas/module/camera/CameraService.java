package com.guicang.nas.module.camera;

import com.guicang.nas.module.camera.dto.CameraRecordVO;
import com.guicang.nas.module.camera.dto.CameraVO;
import java.util.List;

/** 监控摄像头服务：注册信息维护 + 录像查询。 */
public interface CameraService {

  /** 全部摄像头（含录像总数与最近录像时间）。 */
  List<CameraVO> listAll();

  /** 新增或更新摄像头注册信息（名称唯一）。 */
  Camera save(Camera camera);

  /** 删除摄像头注册信息（不删除录像文件）。 */
  void delete(Long id);

  /** 归档发现新摄像头名时自动注册（已存在则忽略）。 */
  void autoRegister(String name);

  /** 某摄像头某日期的录像列表（date 为空则取今天）。 */
  List<CameraRecordVO> records(String camera, String date);
}
